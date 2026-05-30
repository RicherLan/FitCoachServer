package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.AdminLoginRequest;
import com.lanprojects.fitcoach.admin.dto.AdminLoginResponse;
import com.lanprojects.fitcoach.admin.dto.AdminProfileResponse;
import com.lanprojects.fitcoach.admin.dto.ChangePasswordRequest;
import com.lanprojects.fitcoach.admin.entity.AdminUser;
import com.lanprojects.fitcoach.admin.repository.AdminUserRepository;
import com.lanprojects.fitcoach.admin.util.AdminJwtUtils;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.security.LoginAttemptLimiter;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 管理员认证服务 — 协调登录 / token 颁发 / 修改密码。
 * <p>
 * 与客户端 {@link AuthService} 完全独立的鉴权链路：
 * <ul>
 *   <li>登录：username + 明文 password → 比对 BCrypt 哈希；</li>
 *   <li>颁 token：复用 {@code jwt.secret} 但 type=admin_access，与客户端 token 互不识别；</li>
 *   <li>密码：DB 永远只存 BCrypt 哈希；service 边界外接触不到明文。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    /** Admin token 过期小时数（独立配置，默认 8h） */
    public static final String CONFIG_ADMIN_TOKEN_EXPIRE_HOURS = "admin.token_expire_hours";
    private static final int DEFAULT_ADMIN_TOKEN_EXPIRE_HOURS = 8;

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 32;

    // ====== 登录限流（本地 Caffeine，单实例进程内有效） ======
    // username 维度防对单管理员账号穷举密码；IP 维度防同 IP 横向爆破多账号。
    // 跨实例不一致 —— 多副本部署需迁 Redis；admin 后台并发低，V1 单机够用。
    private static final int USERNAME_MAX_ATTEMPTS = 5;
    private static final int IP_MAX_ATTEMPTS = 10;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final LoginAttemptLimiter usernameLimiter =
            new LoginAttemptLimiter(USERNAME_MAX_ATTEMPTS, ATTEMPT_WINDOW, 10_000);
    private final LoginAttemptLimiter ipLimiter =
            new LoginAttemptLimiter(IP_MAX_ATTEMPTS, ATTEMPT_WINDOW, 10_000);

    private final AdminUserRepository adminUserRepository;
    private final SysConfigService sysConfigService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 管理员登录（不带 IP 重载 —— 兼容旧调用 / 单元测试；仅 username 维度限流）。
     *
     * @deprecated 业务方请改用 {@link #login(AdminLoginRequest, String)} 同时传入 IP，启用 IP 维度限流。
     */
    @Deprecated
    public AdminLoginResponse login(AdminLoginRequest request) {
        return login(request, null);
    }

    /**
     * 管理员登录。
     *
     * <p><b>限流策略</b>（{@link LoginAttemptLimiter}，本地 Caffeine 单实例）：
     * <ul>
     *   <li>username 维度：{@value #USERNAME_MAX_ATTEMPTS} 次失败 / {@code ATTEMPT_WINDOW}；</li>
     *   <li>IP 维度：{@value #IP_MAX_ATTEMPTS} 次失败 / {@code ATTEMPT_WINDOW}；</li>
     *   <li>超限抛 {@link ResultCode#ADMIN_LOGIN_RATE_LIMITED}（不告知是哪一维度，避免规避）；</li>
     *   <li>登录成功后<b>只</b>清零 username 维度计数；IP 维度故意保留防爆破其他账号。</li>
     * </ul>
     *
     * @param request  登录请求体
     * @param clientIp 客户端真实 IP；可传 null，传 null 时跳过 IP 维度限流
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String clientIp) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            // 不区分"用户名为空"和"密码为空"，统一报登录失败，避免给暴力枚举提供信号
            throw new BusinessException(ResultCode.ADMIN_LOGIN_FAILED);
        }

        String username = request.getUsername().trim();

        // 1) 限流预检 —— 在 DB 查询之前先挡掉，避免暴力请求把 DB 打满
        if (!usernameLimiter.isAllowed(username)) {
            log.warn("管理员登录被限流（username 维度）, username={}", username);
            throw new BusinessException(ResultCode.ADMIN_LOGIN_RATE_LIMITED);
        }
        if (clientIp != null && !clientIp.isBlank() && !ipLimiter.isAllowed(clientIp)) {
            log.warn("管理员登录被限流（IP 维度）, ip={}", LogUtils.mask(clientIp));
            throw new BusinessException(ResultCode.ADMIN_LOGIN_RATE_LIMITED);
        }

        try {
            AdminUser admin = adminUserRepository.findByUsername(username)
                    // 同样按"账号或密码错误"统一对外，避免泄露账号是否存在
                    .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_LOGIN_FAILED));

            if (!Boolean.TRUE.equals(admin.getEnabled())) {
                log.warn("管理员账号已禁用尝试登录, username={}", username);
                throw new BusinessException(ResultCode.ADMIN_ACCOUNT_DISABLED);
            }

            if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
                log.warn("管理员密码错误, username={}", username);
                throw new BusinessException(ResultCode.ADMIN_LOGIN_FAILED);
            }

            admin.setLastLoginAt(LocalDateTime.now());
            adminUserRepository.save(admin);

            String token = issueToken(admin);
            int expireHours = currentExpireHours();
            // 登录成功 → 清零 username 维度计数；IP 维度故意保留
            usernameLimiter.reset(username);
            log.info("管理员登录成功, username={}, role={}", username, admin.getRole());
            return AdminLoginResponse.builder()
                    .username(admin.getUsername())
                    .displayName(displayNameOrFallback(admin))
                    .role(admin.getRole().name())
                    .token(token)
                    .expiresIn(expireHours * 3600L)
                    .build();
        } catch (BusinessException e) {
            // 仅对"账号或密码错误"类失败累计配额；账号禁用、限流自身不再加权（避免 DoS）
            if (e.getCode() == ResultCode.ADMIN_LOGIN_FAILED.getCode()) {
                int afterName = usernameLimiter.recordFailure(username);
                if (afterName >= USERNAME_MAX_ATTEMPTS) {
                    log.warn("管理员登录失败次数达上限（username 维度）, username={}, count={}/{}",
                            username, afterName, USERNAME_MAX_ATTEMPTS);
                }
                if (clientIp != null && !clientIp.isBlank()) {
                    int afterIp = ipLimiter.recordFailure(clientIp);
                    if (afterIp >= IP_MAX_ATTEMPTS) {
                        log.warn("管理员登录失败次数达上限（IP 维度）, ip={}, count={}/{}",
                                LogUtils.mask(clientIp), afterIp, IP_MAX_ATTEMPTS);
                    }
                }
            }
            throw e;
        }
    }

    /**
     * 通过 username 获取当前管理员资料（拦截器解析 token 后传入 username）
     */
    public AdminProfileResponse getProfile(String username) {
        AdminUser admin = requireAdmin(username);
        return AdminProfileResponse.builder()
                .username(admin.getUsername())
                .displayName(displayNameOrFallback(admin))
                .role(admin.getRole().name())
                .lastLoginAt(toMillis(admin.getLastLoginAt()))
                .build();
    }

    /**
     * 修改密码（必须验证原密码）
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        if (request == null
                || request.getOldPassword() == null
                || request.getNewPassword() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求体不完整");
        }
        String newPwd = request.getNewPassword();
        if (newPwd.length() < MIN_PASSWORD_LENGTH || newPwd.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ResultCode.ADMIN_PASSWORD_INVALID);
        }
        AdminUser admin = requireAdmin(username);
        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPasswordHash())) {
            log.warn("管理员改密失败：原密码错误, username={}", username);
            throw new BusinessException(ResultCode.ADMIN_OLD_PASSWORD_WRONG);
        }
        admin.setPasswordHash(passwordEncoder.encode(newPwd));
        adminUserRepository.save(admin);
        log.info("管理员改密成功, username={}", username);
    }

    /**
     * 拦截器使用：根据 username 获取 admin 实体，并校验 enabled。
     * <p>每个请求都查 DB 一次（admin 接口 QPS 低，可以接受），保证禁用立即生效。
     */
    public AdminUser requireAdmin(String username) {
        AdminUser admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_ACCOUNT_NOT_FOUND));
        if (!Boolean.TRUE.equals(admin.getEnabled())) {
            throw new BusinessException(ResultCode.ADMIN_ACCOUNT_DISABLED);
        }
        return admin;
    }

    // ====== 内部 ======

    private String issueToken(AdminUser admin) {
        String secret = sysConfigService.getValue(AuthService.CONFIG_JWT_SECRET);
        if (secret == null || secret.isBlank()) {
            log.error("JWT 密钥未配置，无法签发 admin token");
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING);
        }
        long expireMs = currentExpireHours() * 3600_000L;
        return AdminJwtUtils.generateAccessToken(admin.getUsername(), admin.getRole().name(), secret, expireMs);
    }

    private int currentExpireHours() {
        return sysConfigService.getIntValue(CONFIG_ADMIN_TOKEN_EXPIRE_HOURS, DEFAULT_ADMIN_TOKEN_EXPIRE_HOURS);
    }

    private String displayNameOrFallback(AdminUser admin) {
        return admin.getDisplayName() != null && !admin.getDisplayName().isBlank()
                ? admin.getDisplayName()
                : admin.getUsername();
    }

    private Long toMillis(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
