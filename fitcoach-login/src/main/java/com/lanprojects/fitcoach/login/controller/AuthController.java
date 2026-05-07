package com.lanprojects.fitcoach.login.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.RefreshTokenRequest;
import com.lanprojects.fitcoach.login.dto.WeChatLoginRequest;
import com.lanprojects.fitcoach.login.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 接口前缀：/api/auth
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    /**
     * 微信登录
     * <p>POST /api/auth/wechat/login
     * <br>Body: { "code": "微信授权码" }
     */
    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@Valid @RequestBody WeChatLoginRequest request) {
        return Result.success(authService.wechatLogin(request.getCode()));
    }

    /**
     * 获取当前用户信息
     * <p>GET /api/auth/me
     * <br>Header: Authorization: Bearer {accessToken}
     */
    @GetMapping("/me")
    public Result<LoginResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(authService.getCurrentUser(extractToken(authorization)));
    }

    /**
     * 用 refresh token 换新的 access token
     * <p>POST /api/auth/refresh
     * <br>Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    /**
     * 登出
     * <p>POST /api/auth/logout
     * <br>Header: Authorization: Bearer {accessToken}
     * <p>当前实现为"占位"接口：仅校验 token 合法、给客户端一个明确入口。
     * <br>TODO 接入 Redis 后在这里把 token 加入黑名单，让其在自然过期前立即失效。
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 仅校验合法性即可；此处不抛错的话登出永远成功（即便 token 已无效）
        try {
            authService.getCurrentUser(extractToken(authorization));
        } catch (BusinessException ignore) {
            // token 已失效也允许登出，幂等处理
        }
        log.info("logout 接口被调用");
        return Result.success();
    }

    /**
     * 健康检查
     * <p>GET /api/auth/ping
     */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    // ====== 辅助方法 ======

    /**
     * 从 Authorization 头里提取 Bearer token；缺失或格式不对直接抛 401，
     * 杜绝下游 null 传入 JwtUtils 时的 NPE。
     */
    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization 请求头");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 必须以 'Bearer ' 开头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 中的 token 为空");
        }
        return token;
    }
}
