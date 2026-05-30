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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户会员状态管理（admin 后台）。
 *
 * <p>路径前缀：/api/admin/membership/users
 * <ul>
 *   <li>{@code GET /{uid}} —— 查指定用户的会员状态</li>
 *   <li>{@code POST /{uid}/grant} —— 手动赠送/续费 N 天会员</li>
 *   <li>{@code POST /{uid}/revoke} —— 立即撤销会员</li>
 * </ul>
 *
 * <p>**为什么不做"会员用户列表"**：会员表是按 user_id 索引的，要做"全部会员用户分页"
 * 涉及和 user 表 join，admin 模块要再写一个跨模块的 service 才行。
 * 当前 admin 已经有用户管理（{@link AdminUserController}），运营从那里搜用户进来即可。
 */
@Slf4j
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
        return Result.success(buildDto(user));
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
            return Result.success(buildDto(user));
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
            return Result.success(buildDto(user));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.REVOKE_MEMBERSHIP, "USER", uid,
                    "revoke membership", e.getMessage());
            throw e;
        }
    }

    // ====== 内部 ======

    /**
     * 组合 user + membership + planDisplayName 为 DTO。
     * <p>没有会员记录时返回 fromUserOnly。
     */
    private AdminUserMembershipDto buildDto(User user) {
        UserMembership membership = membershipService.findByUserId(user.getId()).orElse(null);
        if (membership == null) {
            return AdminUserMembershipDto.fromUserOnly(user);
        }
        // 拿 planDisplayName（避免前端再请求一次套餐表）
        String planDisplayName = null;
        try {
            MembershipPlan plan = membershipService.findPlanByCode(membership.getPlanCode());
            planDisplayName = plan.getDisplayName();
        } catch (BusinessException ignore) {
            // 套餐被物理删除时 fallback：用 planCode 兜底
        }
        return AdminUserMembershipDto.from(user, membership, planDisplayName);
    }

    /**
     * （便捷接口）一次取多个用户的会员状态 — 与用户管理列表 join 用。
     * 入参 {"uids":["xxx","yyy"]}，返回 {uid → status DTO} map。
     */
    @PostMapping("/batch")
    public Result<Map<String, AdminUserMembershipDto>> batch(@RequestBody Map<String, java.util.List<String>> body) {
        java.util.List<String> uids = body == null ? null : body.get("uids");
        if (uids == null || uids.isEmpty()) {
            return Result.success(Map.of());
        }
        java.util.Map<String, AdminUserMembershipDto> out = new java.util.HashMap<>();
        for (User u : userRepository.findByUidIn(uids)) {
            out.put(u.getUid(), buildDto(u));
        }
        return Result.success(out);
    }
}
