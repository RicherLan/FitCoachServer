package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.AdminLoginRequest;
import com.lanprojects.fitcoach.admin.dto.AdminLoginResponse;
import com.lanprojects.fitcoach.admin.dto.AdminProfileResponse;
import com.lanprojects.fitcoach.admin.dto.ChangePasswordRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminAuthService;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员鉴权相关接口。
 * <p>路径前缀：/api/admin/auth
 * <ul>
 *   <li>{@code POST /login} —— 登录，公开接口（拦截器 excludePathPatterns 已放行）</li>
 *   <li>{@code GET /me} —— 获取当前管理员资料（需 token）</li>
 *   <li>{@code PUT /password} —— 修改自己密码（需 token + 原密码）</li>
 * </ul>
 * <p>登录后端接受 JSON：{ "username": "admin", "password": "admin123" }；
 * 返回体含 token + 角色，前端持久化到 localStorage 即可，后续请求加
 * {@code Authorization: Bearer <token>}。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAuditLogService auditLogService;

    /** 登录 */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(
            @RequestBody(required = false) AdminLoginRequest request,
            HttpServletRequest httpRequest) {
        // 透传 IP 进入 AdminAuthService → 启用 username + IP 双维度本地限流
        String clientIp = ClientIpResolver.resolve(httpRequest);
        // 审计落账：登录是高频敏感操作，成功 / 失败都要记录
        String attemptedUsername = request == null ? null : request.getUsername();
        try {
            AdminLoginResponse resp = adminAuthService.login(request, clientIp);
            auditLogService.logSuccessAs(httpRequest, resp.getUsername(), resp.getRole(),
                    AdminAuditAction.LOGIN_SUCCESS, "SELF", resp.getUsername(),
                    "login success");
            return Result.success(resp);
        } catch (RuntimeException e) {
            auditLogService.logFailureAs(httpRequest,
                    attemptedUsername == null ? "(unknown)" : attemptedUsername,
                    null,
                    AdminAuditAction.LOGIN_FAILED, "SELF",
                    attemptedUsername,
                    "login failed", e.getMessage());
            throw e;
        }
    }

    /** 当前管理员资料 — username/role 来自 {@link AdminAuthInterceptor} 写入的 request attribute */
    @GetMapping("/me")
    public Result<AdminProfileResponse> me(HttpServletRequest request) {
        String username = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        return Result.success(adminAuthService.getProfile(username));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(HttpServletRequest request,
                                       @RequestBody(required = false) ChangePasswordRequest body) {
        String username = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            adminAuthService.changePassword(username, body);
            auditLogService.logSuccess(request, AdminAuditAction.CHANGE_PASSWORD,
                    "SELF", username, "change own password");
            return Result.success();
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.CHANGE_PASSWORD,
                    "SELF", username, "change own password", e.getMessage());
            throw e;
        }
    }
}
