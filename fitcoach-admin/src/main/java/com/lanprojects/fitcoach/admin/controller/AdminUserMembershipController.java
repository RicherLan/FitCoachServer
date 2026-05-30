package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.membership.AdminGrantMembershipRequest;
import com.lanprojects.fitcoach.admin.dto.membership.AdminUserMembershipDto;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.entity.UserMembership;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户会员状态管理（admin 后台）。
 *
 * <p>路径前缀：/api/admin/membership/users
 * <ul>
 *   <li>{@code GET /{uid}} —— 查指定用户的会员状态</li>
 *   <li>{@code POST /{uid}/grant} —— 手动赠送/续费 N 天会员</li>
 *   <li>{@code POST /{uid}/revoke} —— 立即撤销会员</li>
 *   <li>{@code POST /batch} —— 批量按 uids 查会员状态（列表 join 用）</li>
 * </ul>
 *
 * <p>**为什么不做"会员用户列表"**：会员表是按 user_id 索引的，要做"全部会员用户分页"
 * 涉及和 user 表 join，admin 模块要再写一个跨模块的 service 才行。
 * 当前 admin 已经有用户管理（{@link AdminUserController}），运营从那里搜用户进来即可。
 *
 * <p>**N+1 防护（P2-3）**：内部 {@link #buildDto} 接收预加载的 membership/plan map，
 * 避免在循环中逐条 query。batch 接口先批量取 membership + plan，整体只 3 次 SQL。
 */
@Slf4j
@Tag(name = "后台-用户会员", description = "查/赠送/撤销指定用户的会员（按 uid 操作）")
@RestController
@RequestMapping("/api/admin/membership/users")
@RequiredArgsConstructor
public class AdminUserMembershipController {

    private final MembershipService membershipService;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    /** 查单个用户的会员状态 */
    @GetMapping("/{uid}")
    public Result<AdminUserMembershipDto> detail(@PathVariable("uid") String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return Result.success(buildDtoForSingle(user));
    }

    /** 手动赠送 N 天会员（首次开通 / 续费叠加） */
    @PostMapping("/{uid}/grant")
    public Result<AdminUserMembershipDto> grant(
            HttpServletRequest request,
            @PathVariable("uid") String uid,
            @Valid @RequestBody AdminGrantMembershipRequest body) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        // operatorOrderId 用 GIFT_<时间戳>_<操作员> 格式，落到 user_membership.last_order_id
        String operatorOrderId = "GIFT_" + System.currentTimeMillis() + "_" + (operator == null ? "admin" : operator);
        String summary = String.format("grant %d days, planCode=%s, reason=%s",
                body.getDays(), body.getPlanCode(), body.getReason());
        try {
            membershipService.grantDays(user.getId(), body.getPlanCode(), body.getDays(), operatorOrderId);
            log.info("[admin] {} 给用户 {} 赠送 {} 天会员（planCode={}, reason={}）",
                    operator, uid, body.getDays(), body.getPlanCode(), body.getReason());
            auditLogService.logSuccess(request, AdminAuditAction.GRANT_MEMBERSHIP, "USER", uid, summary);
            return Result.success(buildDtoForSingle(user));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.GRANT_MEMBERSHIP, "USER", uid,
                    summary, e.getMessage());
            throw e;
        }
    }

    /** 立即撤销会员 */
    @PostMapping("/{uid}/revoke")
    public Result<AdminUserMembershipDto> revoke(
            HttpServletRequest request,
            @PathVariable("uid") String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            membershipService.revoke(user.getId());
            log.info("[admin] {} 撤销用户 {} 的会员", operator, uid);
            auditLogService.logSuccess(request, AdminAuditAction.REVOKE_MEMBERSHIP, "USER", uid,
                    "revoke membership");
            return Result.success(buildDtoForSingle(user));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.REVOKE_MEMBERSHIP, "USER", uid,
                    "revoke membership", e.getMessage());
            throw e;
        }
    }

    /**
     * （便捷接口）一次取多个用户的会员状态 — 与用户管理列表 join 用。
     * <p>入参 {"uids":["xxx","yyy"]}，返回 {uid → status DTO} map。
     *
     * <p>N+1 防护：先一次拿全 users，再一次批量拿全 memberships，再一次批量拿涉及的 plans。
     * 总共 3 次 SQL，与 uids 数量无关。
     */
    @PostMapping("/batch")
    public Result<Map<String, AdminUserMembershipDto>> batch(@RequestBody Map<String, List<String>> body) {
        List<String> uids = body == null ? null : body.get("uids");
        if (uids == null || uids.isEmpty()) {
            return Result.success(Map.of());
        }
        // 1. 一次拉 users
        List<User> users = userRepository.findByUidIn(uids);
        if (users.isEmpty()) {
            return Result.success(Map.of());
        }
        // 2. 一次批量拉所有 userId 的 membership
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, UserMembership> membershipMap = membershipService.findMembershipsByUserIds(userIds);
        // 3. 收集所有涉及的 planCode，一次批量拉 plans
        Set<String> planCodes = membershipMap.values().stream()
                .map(UserMembership::getPlanCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, MembershipPlan> planMap = membershipService.findPlansByCodes(planCodes);

        Map<String, AdminUserMembershipDto> out = new HashMap<>(users.size() * 2);
        for (User u : users) {
            out.put(u.getUid(), buildDto(u, membershipMap.get(u.getId()), planMap));
        }
        return Result.success(out);
    }

    // ====== 内部 ======

    /**
     * 单条场景的便捷封装：复用 {@link #buildDto} 主逻辑，但只 query 当前 user 的 1 条 membership + 1 条 plan，
     * 行为完全等价于改造前的逻辑（用于 detail/grant/revoke 这种单条接口）。
     */
    private AdminUserMembershipDto buildDtoForSingle(User user) {
        UserMembership membership = membershipService.findByUserId(user.getId()).orElse(null);
        if (membership == null) {
            return AdminUserMembershipDto.fromUserOnly(user);
        }
        Map<String, MembershipPlan> planMap = membershipService
                .findPlansByCodes(List.of(membership.getPlanCode()));
        return buildDto(user, membership, planMap);
    }

    /**
     * 组合 user + membership + planDisplayName 为 DTO（N+1 安全版）。
     * <p>没有会员记录时返回 fromUserOnly；plan 在 map 中缺失时（套餐被物理删除）planDisplayName 兜底为 null。
     */
    private AdminUserMembershipDto buildDto(User user,
                                            UserMembership membership,
                                            Map<String, MembershipPlan> planMap) {
        if (membership == null) {
            return AdminUserMembershipDto.fromUserOnly(user);
        }
        MembershipPlan plan = planMap == null ? null : planMap.get(membership.getPlanCode());
        String planDisplayName = plan == null ? null : plan.getDisplayName();
        return AdminUserMembershipDto.from(user, membership, planDisplayName);
    }
}
