package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.CreateUserRequest;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.ResetUserPasswordRequest;
import com.lanprojects.fitcoach.admin.dto.UpdateUserStatusRequest;
import com.lanprojects.fitcoach.admin.dto.UserDetailDto;
import com.lanprojects.fitcoach.admin.dto.UserSummaryDto;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminUserService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.CsvHttpResponseUtil;
import com.lanprojects.fitcoach.login.entity.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户管理（admin 后台）。
 * <p>路径前缀：/api/admin/users
 * <ul>
 *   <li>{@code GET /} —— 分页列表，支持 keyword（昵称/手机号/uid/account）/ enabled / loginType 过滤</li>
 *   <li>{@code GET /{uid}} —— 详情</li>
 *   <li>{@code POST /} —— 手动创建用户（运营/客服/QA 内部账号），自动生成 account</li>
 *   <li>{@code PUT /{uid}/status} —— 启用 / 禁用</li>
 *   <li>{@code POST /{uid}/reset-password} —— 重置用户密码（独立 audit 记录）</li>
 *   <li>{@code GET /export} —— 按筛选条件导出 CSV</li>
 * </ul>
 * <p>所有写操作走 {@link AdminAuthInterceptor} 的角色拦截，VIEWER 直接被拒（7008）。
 */
@Slf4j
@Tag(name = "后台-用户管理", description = "C 端用户分页/详情/创建/启停/重置密码（VIEWER 只读）")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminAuditLogService auditLogService;

    /**
     * 分页查询用户
     *
     * @param page      页码（1-based，默认 1）
     * @param size      每页条数（默认 20，service 层硬上限 200）
     * @param keyword   昵称 / phone / uid 关键字模糊匹配，可选
     * @param enabled   启用状态过滤，可选
     * @param loginType 登录方式过滤（WECHAT/PHONE/...），可选
     */
    @GetMapping
    public Result<PageResponse<UserSummaryDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "loginType", required = false) String loginType) {
        User.LoginType lt = parseLoginType(loginType);
        return Result.success(adminUserService.listUsers(page, size, keyword, enabled, lt));
    }

    /** 用户详情 */
    @GetMapping("/{uid}")
    public Result<UserDetailDto> detail(@PathVariable("uid") String uid) {
        return Result.success(adminUserService.getUserDetail(uid));
    }

    /**
     * admin 后台手动创建 C 端用户（{@code registrationSource=ADMIN_CREATED}）。
     * <p>account 由服务端 {@code AccountGenerator} 自动生成，admin 不可手动指定，避免靓号被滥用。
     */
    @PostMapping
    public Result<UserDetailDto> create(HttpServletRequest request,
                                        @Valid @RequestBody CreateUserRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            UserDetailDto dto = adminUserService.createUser(body, operator);
            auditLogService.logSuccess(request, AdminAuditAction.CREATE_USER, "USER", dto.getUid(),
                    String.format("account=%s, nickname=%s", dto.getAccount(), dto.getNickname()));
            return Result.success(dto);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.CREATE_USER, "USER", null,
                    String.format("nickname=%s", body == null ? null : body.getNickname()),
                    e.getMessage());
            throw e;
        }
    }

    /** 启用 / 禁用用户 */
    @PutMapping("/{uid}/status")
    public Result<UserDetailDto> updateStatus(HttpServletRequest request,
                                              @PathVariable("uid") String uid,
                                              @RequestBody(required = false) UpdateUserStatusRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        Boolean enabled = body == null ? null : body.getEnabled();
        AdminAuditAction action = Boolean.FALSE.equals(enabled)
                ? AdminAuditAction.BAN_USER
                : AdminAuditAction.UNBAN_USER;
        try {
            UserDetailDto result = adminUserService.updateStatus(uid, body, operator);
            auditLogService.logSuccess(request, action, "USER", uid,
                    "set enabled=" + enabled);
            return Result.success(result);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, action, "USER", uid,
                    "set enabled=" + enabled, e.getMessage());
            throw e;
        }
    }

    /**
     * 重置用户密码 —— 独立接口，单独 audit 一条 {@code RESET_USER_PASSWORD}。
     * <p>summary 不会带密码明文 / 哈希，仅记录目标 uid。
     */
    @PostMapping("/{uid}/reset-password")
    public Result<UserDetailDto> resetPassword(HttpServletRequest request,
                                               @PathVariable("uid") String uid,
                                               @Valid @RequestBody ResetUserPasswordRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            UserDetailDto dto = adminUserService.resetPassword(uid, body, operator);
            auditLogService.logSuccess(request, AdminAuditAction.RESET_USER_PASSWORD, "USER", uid,
                    String.format("account=%s", dto.getAccount()));
            return Result.success(dto);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.RESET_USER_PASSWORD, "USER", uid,
                    "reset password", e.getMessage());
            throw e;
        }
    }

    /**
     * P2-12：按当前筛选条件导出用户 CSV。
     * <p>路径：{@code GET /api/admin/users/export}
     * <p>响应：text/csv; charset=UTF-8（含 BOM，Excel 直接打开中文不乱码）。
     * <p>最多 {@link AdminUserService#MAX_EXPORT_SIZE} 条，超过部分需要进一步过滤后再导。
     * <p>VIEWER 只读但允许导出（导出≈高级查询，不写库；写库才需要 OPERATOR）。
     */
    @GetMapping("/export")
    public void exportCsv(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "enabled", required = false) Boolean enabled,
                          @RequestParam(value = "loginType", required = false) String loginType) throws IOException {
        User.LoginType lt = parseLoginType(loginType);
        List<User> users = adminUserService.exportUsers(keyword, enabled, lt);

        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        auditLogService.logSuccess(request, AdminAuditAction.EXPORT_USERS, "USER", null,
                "rows=" + users.size() + ", keyword=" + keyword + ", enabled=" + enabled + ", loginType=" + loginType);
        log.info("导出用户 CSV, operator={}, rows={}", operator, users.size());

        CsvHttpResponseUtil.write(response, "users",
                List.of("uid", "account", "昵称", "最近登录方式", "注册来源", "性别", "手机号", "启用", "注册时间", "最后登录时间", "最后活跃时间"),
                users, u -> List.of(
                        nullToEmpty(u.getUid()),
                        nullToEmpty(u.getAccount()),
                        nullToEmpty(u.getNickname()),
                        u.getLoginType() == null ? "" : u.getLoginType().name(),
                        u.getRegistrationSource() == null ? "" : u.getRegistrationSource().name(),
                        u.getGender() == null ? "" : String.valueOf(u.getGender()),
                        maskPhone(u.getPhone()),
                        u.getEnabled() == null ? "" : (u.getEnabled() ? "是" : "否"),
                        fmtIso(u.getCreatedAt()),
                        fmtIso(u.getLastLoginAt()),
                        fmtIso(u.getLastActiveAt())
                ));
    }

    // ====== 内部 ======

    private User.LoginType parseLoginType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return User.LoginType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "loginType 参数不合法：" + raw);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 列表导出与列表展示保持一致：仅展示脱敏手机号 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone == null ? "" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static String fmtIso(LocalDateTime t) {
        return t == null ? "" : t.format(ISO_FMT);
    }
}
