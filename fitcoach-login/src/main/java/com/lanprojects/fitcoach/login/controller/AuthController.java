package com.lanprojects.fitcoach.login.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.WeChatLoginRequest;
import com.lanprojects.fitcoach.login.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 接口前缀：/api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信登录
     * <p>
     * POST /api/auth/wechat/login
     * Body: { "code": "微信授权码" }
     *
     * @return 用户信息 + JWT token
     */
    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@Valid @RequestBody WeChatLoginRequest request) {
        LoginResponse response = authService.wechatLogin(request.getCode());
        return Result.success(response);
    }

    /**
     * 获取当前用户信息
     * <p>
     * GET /api/auth/me
     * Header: Authorization: Bearer {token}
     */
    @GetMapping("/me")
    public Result<LoginResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        LoginResponse response = authService.getCurrentUser(token);
        return Result.success(response);
    }

    /**
     * 健康检查
     * <p>
     * GET /api/auth/ping
     */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    // ====== 辅助方法 ======

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
