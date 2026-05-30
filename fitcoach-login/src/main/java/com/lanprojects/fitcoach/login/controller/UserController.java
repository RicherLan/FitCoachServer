package com.lanprojects.fitcoach.login.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.SetPasswordRequest;
import com.lanprojects.fitcoach.login.dto.UpdateProfileRequest;
import com.lanprojects.fitcoach.login.service.AuthService;
import com.lanprojects.fitcoach.login.service.PasswordService;
import com.lanprojects.fitcoach.login.service.UserProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器
 * <p>接口前缀：/api/user
 * <p>所有接口都需要 Authorization: Bearer {accessToken}。
 * <p>token 校验复用 {@link AuthService#getCurrentUser(String)} 拿 uid，
 * 不引入 Spring Security 这种重武器，保持现有项目轻量。
 */
@Slf4j
@Tag(name = "客户端-用户资料", description = "获取/更新自己的资料、头像上传、设置/修改密码、注销账号")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final UserProfileService userProfileService;
    private final PasswordService passwordService;

    /**
     * 获取当前用户资料 — 别名接口，方便客户端按业务划分调用，
     * 与 {@code /api/auth/me} 完全等价。
     */
    @GetMapping("/me")
    public Result<LoginResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(authService.getCurrentUser(extractToken(authorization)));
    }

    /**
     * 更新用户资料（昵称 / 性别）
     * <p>PATCH /api/user/profile
     * <br>Body: {@link UpdateProfileRequest}（所有字段可选，至少传 1 个）
     * <br>Returns: 更新后的 LoginResponse（不带 token）
     */
    @PatchMapping("/profile")
    public Result<LoginResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) UpdateProfileRequest request) {
        String uid = currentUid(authorization);
        return Result.success(userProfileService.updateProfile(uid, request));
    }

    /**
     * 上传头像
     * <p>POST /api/user/avatar  (multipart/form-data, field=file)
     * <br>Returns: 更新后的 LoginResponse（含新 avatarUrl）
     * <p>客户端职责：拍照/选图后必须本地压缩到 ~200KB 内（512x512 + quality 0.7 即可），
     * 服务端兜底校验 maxSizeBytes（默认 2MB）+ contentType 白名单。
     */
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public Result<LoginResponse> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        String uid = currentUid(authorization);
        return Result.success(userProfileService.updateAvatar(uid, file));
    }

    /**
     * 当前用户是否已设置密码 —— 客户端"账号安全"页据此区分"设置密码 / 修改密码"两种 UI 走向。
     * <p>GET /api/user/password/exists
     * <br>Returns: { "exists": true|false }
     */
    @GetMapping("/password/exists")
    public Result<Map<String, Boolean>> passwordExists(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String uid = currentUid(authorization);
        return Result.success(Map.of("exists", passwordService.hasPassword(uid)));
    }

    /**
     * 设置 / 修改密码（合并接口）
     * <p>POST /api/user/password
     * <br>Body: { "newPassword": "Abc123!", "oldPassword": "...", "otpCode": "..." }
     * <ul>
     *     <li>当前未设置密码 → 必须提供 otpCode（先调 /api/auth/phone/sendCode）；</li>
     *     <li>当前已设置密码 → oldPassword 与 otpCode 二选一即可。</li>
     * </ul>
     * <p>密码格式 6-32 位 + 至少 1 字母 + 1 数字。
     */
    @PostMapping("/password")
    public Result<Void> setOrChangePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SetPasswordRequest request) {
        String uid = currentUid(authorization);
        passwordService.setOrChangePassword(uid, request);
        return Result.success();
    }

    // ====== 辅助 ======

    /**
     * 从 Authorization 头取出 access token 后调 AuthService 解析出 uid。
     * <p>失败统一抛 401，避免 service 层再处理 token 校验。
     */
    private String currentUid(String authorization) {
        // getCurrentUser 内部已做 token 校验 + 用户存在 / enabled 校验，复用即可
        return authService.getCurrentUser(extractToken(authorization)).getUid();
    }

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
