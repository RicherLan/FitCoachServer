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
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AdminUserRepository adminUserRepository;
    private final SysConfigService sysConfigService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 管理员登录
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            // 不区分"用户名为空"和"密码为空"，统一报登录失败，避免给暴力枚举提供信号
            throw new BusinessException(ResultCode.ADMIN_LOGIN_FAILED);
        }

        String username = request.getUsername().trim();
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
        log.info("管理员登录成功, username={}, role={}", username, admin.getRole());
        return AdminLoginResponse.builder()
                .username(admin.getUsername())
                .displayName(displayNameOrFallback(admin))
                .role(admin.getRole().name())
                .token(token)
                .expiresIn(expireHours * 3600L)
                .build();
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
