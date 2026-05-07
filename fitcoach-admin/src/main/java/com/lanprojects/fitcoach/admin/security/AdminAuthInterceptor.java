package com.lanprojects.fitcoach.admin.security;

import com.lanprojects.fitcoach.admin.entity.AdminUser;
import com.lanprojects.fitcoach.admin.service.AdminAuthService;
import com.lanprojects.fitcoach.admin.util.AdminJwtUtils;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin 接口统一鉴权拦截器。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>读取 Authorization 头，要求 {@code Bearer xxx} 格式；</li>
 *   <li>用 {@link AdminJwtUtils#parseAndVerify} 校验签名 + 类型 + 过期；</li>
 *   <li>查 DB 校验 admin 仍存在且 enabled，把 username/role 写入 request attribute 供 controller 取用；</li>
 *   <li>对写操作（POST/PUT/PATCH/DELETE）额外校验角色 {@code canWrite()}，VIEWER 拦下。</li>
 * </ol>
 * <p>
 * 拦截范围在 {@link AdminWebMvcConfig#addInterceptors} 中按 path 配置，
 * 登录接口 {@code /api/admin/auth/login} 走 excludePathPatterns 放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ADMIN_USERNAME = "admin.username";
    public static final String ATTR_ADMIN_ROLE = "admin.role";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuthService adminAuthService;
    private final SysConfigService sysConfigService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank() || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.ADMIN_UNAUTHORIZED, "缺少 Authorization 请求头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ResultCode.ADMIN_UNAUTHORIZED, "Authorization 中的 token 为空");
        }

        String secret = sysConfigService.getValue(AuthService.CONFIG_JWT_SECRET);
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING);
        }

        AdminJwtUtils.AdminTokenPayload payload = AdminJwtUtils.parseAndVerify(token, secret);

        // 二次落地校验：账号仍存在 + enabled（防止禁用后 token 还能用）
        AdminUser admin = adminAuthService.requireAdmin(payload.username());

        // 写操作鉴权：VIEWER 一律不许写
        String method = request.getMethod();
        if (isWriteMethod(method) && !admin.getRole().canWrite()) {
            log.warn("VIEWER 角色尝试写操作被拒, username={}, method={}, uri={}",
                    admin.getUsername(), method, request.getRequestURI());
            throw new BusinessException(ResultCode.ADMIN_PERMISSION_DENIED);
        }

        request.setAttribute(ATTR_ADMIN_USERNAME, admin.getUsername());
        request.setAttribute(ATTR_ADMIN_ROLE, admin.getRole().name());
        return true;
    }

    /** 写操作判定 — 修改密码也算写，但允许（自己改自己），所以这里不再额外细分 */
    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }
}
