package com.lanprojects.fitcoach.login.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.security.ClientIpResolver;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.PasswordLoginRequest;
import com.lanprojects.fitcoach.login.dto.PhoneLoginRequest;
import com.lanprojects.fitcoach.login.dto.RefreshTokenRequest;
import com.lanprojects.fitcoach.login.dto.SendCodeRequest;
import com.lanprojects.fitcoach.login.dto.WeChatLoginRequest;
import com.lanprojects.fitcoach.login.service.AuthService;
import com.lanprojects.fitcoach.login.service.CaptchaService;
import com.lanprojects.fitcoach.login.service.OtpService;
import com.lanprojects.fitcoach.login.service.PasswordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@Tag(name = "客户端-登录认证", description = "微信/手机号/密码登录、OTP 发送、token 刷新、修改密码")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final OtpService otpService;
    private final PasswordService passwordService;

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
     * 发送手机号验证码
     * <p>POST /api/auth/phone/sendCode
     * <br>Body: { "phone": "13812345678", "scene": "LOGIN", "captchaTicket": "...", "captchaRandstr": "..." }
     * <p>流程：先校验腾讯行为验证码 → 再走 OtpService 频控 + 发送。
     * <p>OtpService 内部已做：60s 重发冷却 / 1h 单号上限 / IP 限频 / OTP 失败次数。
     */
    @PostMapping("/phone/sendCode")
    public Result<Void> sendPhoneCode(
            @Valid @RequestBody SendCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        // 先校验人机验证码（captcha.enabled=false 时自动跳过）
        captchaService.verify(request.getCaptchaTicket(), request.getCaptchaRandstr(), clientIp);
        // 验证通过 → 发送 OTP
        otpService.requestOtp(request.getPhone(), clientIp);
        return Result.success();
    }

    /**
     * 手机号 + 验证码登录
     * <p>POST /api/auth/phone/login
     * <br>Body: { "phone": "13812345678", "code": "123456" }
     * <p>新手机号自动注册账号，无需额外注册流程。
     */
    @PostMapping("/phone/login")
    public Result<LoginResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        // 1) 先校验 OTP，校验失败直接抛业务码（OTP 内部限流也会触发对应错误）
        otpService.verifyOtp(request.getPhone(), request.getCode());
        // 2) 校验通过 → 进入 findOrCreate + 颁 token
        return Result.success(authService.phoneLogin(request.getPhone()));
    }

    /**
     * 手机号 + 密码登录
     * <p>POST /api/auth/login/password
     * <br>Body: { "phone": "13812345678", "password": "Abc123!" }
     * <p>密码登录不会自动注册账号；用户必须先通过 OTP 登录后在"账号安全"里设置过密码。
     * 校验失败统一回 7401 PASSWORD_LOGIN_FAILED，避免泄露"账号是否存在 / 是否设置过密码"。
     */
    @PostMapping("/login/password")
    public Result<LoginResponse> passwordLogin(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest httpRequest) {
        // 透传 IP 进入 PasswordService → 启用 phone + IP 双维度本地限流，
        // 防止单账号枚举密码 / 同 IP 撒网爆破多账号。
        String clientIp = ClientIpResolver.resolve(httpRequest);
        return Result.success(passwordService.login(request.getPhone(), request.getPassword(), clientIp));
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

    /**
     * 取真实客户端 IP —— 已抽到 {@link ClientIpResolver}（common-security），此处保留薄包装便于内部调用。
     */
    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
}
