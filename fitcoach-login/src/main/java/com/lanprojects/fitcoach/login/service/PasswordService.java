package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.SetPasswordRequest;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 用户密码服务 — 密码登录 / 设置密码 / 修改密码 / 校验是否已设置密码。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>密码强度统一校验：6-32 位 + 至少 1 字母 + 1 数字（与 admin 模块保持一致）；</li>
 *     <li>密码登录失败统一报 {@link ResultCode#PASSWORD_LOGIN_FAILED}，不区分"用户不存在 / 未设置密码 / 密码错误"，
 *         避免给暴力枚举提供信号；</li>
 *     <li>"未设置密码"分支单独存在 {@link ResultCode#PASSWORD_NOT_SET}，仅用于"设置/修改密码"接口
 *         （已登录态，可以告诉用户"你还没设置过密码"）；</li>
 *     <li>合并接口：{@link #setOrChangePassword}
 *         <ul>
 *             <li>未设置密码 → 必须传 otpCode（通过 OTP 验证身份）；</li>
 *             <li>已设置密码 → oldPassword 或 otpCode 二选一（用户记不住旧密码也能改）。</li>
 *         </ul>
 *     </li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    /** 密码强度规则：6-32 位 + 至少 1 字母 + 1 数字（特殊字符可选） */
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 32;
    private static final Pattern PASSWORD_RULE_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()\\-_=+\\[\\]{};:,.<>?/]{6,32}$");

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final AuthService authService;

    /**
     * 密码登录（手机号 + 密码）
     * <p>校验失败一律回 {@link ResultCode#PASSWORD_LOGIN_FAILED}，避免泄露账号是否存在 / 是否设置过密码。
     */
    @Transactional
    public LoginResponse login(String phone, String password) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> {
                    log.info("密码登录失败：手机号不存在, phone={}", LogUtils.mask(phone));
                    return new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
                });

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("密码登录失败：账号已禁用, uid={}", user.getUid());
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        String hash = user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            log.info("密码登录失败：未设置密码, uid={}", user.getUid());
            // 同样回 LOGIN_FAILED 而不是 PASSWORD_NOT_SET：未登录态没法证明账号属主，告诉对方"该手机号未设密码"
            // 等于帮攻击者枚举哪些手机号是新用户，反过来又能用 OTP 流程接管
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(password, hash)) {
            log.info("密码登录失败：密码错误, uid={}", user.getUid());
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }

        // 校验通过 → 复用 AuthService.phoneLogin 的"老用户登录"路径颁 token
        // 注意：这里不能复用 phoneLogin 整体（它会 findOrCreate，密码登录不应自动创号）
        // 所以走 buildLoginResponse 等价路径：直接走 authService 颁 token
        log.info("密码登录成功, uid={}, phone={}", user.getUid(), LogUtils.mask(phone));
        return authService.phoneLogin(phone);
    }

    /**
     * 检查指定 uid 是否已设置过密码（供 RN 区分"设置密码 / 修改密码"两种 UI 走向）。
     */
    public boolean hasPassword(String uid) {
        return userRepository.findByUid(uid)
                .map(u -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
    }

    /**
     * 设置 / 修改密码（合并接口）。
     * <ul>
     *     <li>当前未设置密码 → 必须传 otpCode（先调 /api/auth/phone/sendCode 拿验证码）；</li>
     *     <li>当前已设置密码 → oldPassword 与 otpCode 二选一即可，便于"忘记旧密码"用户走 OTP 路径。</li>
     * </ul>
     */
    @Transactional
    public void setOrChangePassword(String uid, SetPasswordRequest request) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        String newPassword = request.getNewPassword();
        validatePasswordStrength(newPassword);

        boolean alreadyHas = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        String oldPassword = nullIfBlank(request.getOldPassword());
        String otpCode = nullIfBlank(request.getOtpCode());

        if (!alreadyHas) {
            // 首次设置：必须有 OTP（防止 token 被盗后随手设密码 → 直接夺号）
            if (otpCode == null) {
                throw new BusinessException(ResultCode.PASSWORD_OTP_REQUIRED);
            }
            String phone = user.getPhone();
            if (phone == null || phone.isBlank()) {
                // 微信等无手机号用户，先要求绑手机号才能设密码（后续接口再补）
                throw new BusinessException(ResultCode.PASSWORD_PHONE_REQUIRED);
            }
            otpService.verifyOtp(phone, otpCode);
        } else {
            // 修改密码：oldPassword 或 otpCode 二选一
            if (oldPassword == null && otpCode == null) {
                throw new BusinessException(ResultCode.PASSWORD_VERIFY_REQUIRED);
            }
            if (oldPassword != null) {
                if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
                    log.warn("修改密码失败：旧密码不正确, uid={}", uid);
                    throw new BusinessException(ResultCode.PASSWORD_OLD_WRONG);
                }
            } else {
                String phone = user.getPhone();
                if (phone == null || phone.isBlank()) {
                    throw new BusinessException(ResultCode.PASSWORD_PHONE_REQUIRED);
                }
                otpService.verifyOtp(phone, otpCode);
            }
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("用户密码已{}, uid={}", alreadyHas ? "修改" : "设置", uid);
    }

    /** 密码强度校验：6-32 位 + 至少 1 字母 + 1 数字 */
    private void validatePasswordStrength(String password) {
        if (password == null) {
            throw new BusinessException(ResultCode.PASSWORD_FORMAT_INVALID);
        }
        int len = password.length();
        if (len < MIN_PASSWORD_LENGTH || len > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ResultCode.PASSWORD_FORMAT_INVALID);
        }
        if (!PASSWORD_RULE_PATTERN.matcher(password).matches()) {
            throw new BusinessException(ResultCode.PASSWORD_FORMAT_INVALID);
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
