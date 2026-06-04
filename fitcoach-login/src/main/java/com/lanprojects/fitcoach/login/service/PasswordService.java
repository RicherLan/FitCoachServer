package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.security.LoginAttemptLimiter;
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

import java.time.Duration;
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

    // ====== 登录限流（本地 Caffeine 实现，单实例进程内有效） ======
    // 跨实例不一致 —— 多副本部署需迁 Redis，当前 V1 单机够用。
    // phone / account 维度防对单账号枚举密码；IP 维度防同 IP 撒网爆破多账号。
    private static final int PHONE_MAX_ATTEMPTS = 5;
    private static final int ACCOUNT_MAX_ATTEMPTS = 5;
    private static final int IP_MAX_ATTEMPTS = 20;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private final LoginAttemptLimiter phoneLimiter =
            new LoginAttemptLimiter(PHONE_MAX_ATTEMPTS, ATTEMPT_WINDOW, 100_000);
    private final LoginAttemptLimiter accountLimiter =
            new LoginAttemptLimiter(ACCOUNT_MAX_ATTEMPTS, ATTEMPT_WINDOW, 100_000);
    private final LoginAttemptLimiter ipLimiter =
            new LoginAttemptLimiter(IP_MAX_ATTEMPTS, ATTEMPT_WINDOW, 50_000);

    /**
     * 密码登录（手机号 + 密码），不带 clientIp 兼容旧调用（仅做 phone 维度限流）。
     *
     * @deprecated 业务方请改用 {@link #login(String, String, String)} 同时传入 IP，启用 IP 维度限流。
     */
    @Deprecated
    public LoginResponse login(String phone, String password) {
        return login(phone, password, null);
    }

    /**
     * 密码登录（手机号 + 密码 + 客户端 IP）。
     *
     * <p><b>限流策略</b>（{@link LoginAttemptLimiter}，本地 Caffeine 单实例）：
     * <ul>
     *   <li>phone 维度：{@value #PHONE_MAX_ATTEMPTS} 次失败 / {@code ATTEMPT_WINDOW}；</li>
     *   <li>IP 维度：{@value #IP_MAX_ATTEMPTS} 次失败 / {@code ATTEMPT_WINDOW}；</li>
     *   <li>超限抛 {@link ResultCode#PASSWORD_LOGIN_RATE_LIMITED}（不告知是哪一维度，避免攻击者按维度规避）；</li>
     *   <li>登录成功后<b>只</b>清零 phone 维度计数（防止单点突破后用同 IP 爆破其他账号）。</li>
     * </ul>
     *
     * <p><b>校验失败</b>一律回 {@link ResultCode#PASSWORD_LOGIN_FAILED}，避免泄露"账号是否存在 / 是否设置过密码"。
     *
     * @param phone     手机号（用于 phone 维度限流 key + DB 查询）
     * @param password  明文密码（BCrypt 比对，本方法不落任何日志）
     * @param clientIp  客户端真实 IP；可传 null，传 null 时跳过 IP 维度限流
     */
    @Transactional
    public LoginResponse login(String phone, String password, String clientIp) {
        // 1) 限流预检 —— 在 DB 查询之前先挡掉，避免暴力请求把 DB 打满
        if (phone != null && !phone.isBlank() && !phoneLimiter.isAllowed(phone)) {
            log.warn("密码登录被限流（phone 维度）, phone={}", LogUtils.mask(phone));
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_RATE_LIMITED);
        }
        if (clientIp != null && !clientIp.isBlank() && !ipLimiter.isAllowed(clientIp)) {
            log.warn("密码登录被限流（IP 维度）, ip={}", LogUtils.mask(clientIp));
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_RATE_LIMITED);
        }

        try {
            User user = userRepository.findByPhone(phone)
                    .orElseThrow(() -> {
                        log.info("密码登录失败：手机号不存在, phone={}", LogUtils.mask(phone));
                        return new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
                    });

            if (!Boolean.TRUE.equals(user.getEnabled())) {
                log.warn("密码登录失败：账号已禁用, uid={}", user.getUid());
                // 账号禁用不计入"密码错误"配额（这条本就不能登录，记了也没意义；
                // 但会让攻击者通过观测计数差异判断账号是否存在 → 反而提供枚举信号）
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

            // 校验通过 → 走 loginExistingUser：仅颁 token / rotate sid / 更新 lastLoginAt + loginType=PHONE，
            // 不会 findOrCreate（密码登录不允许自动创号）
            LoginResponse resp = authService.loginExistingUser(user, User.LoginType.PHONE);
            // 登录成功 → 清零 phone 维度计数；IP 维度故意保留，防止单点突破后爆破其他账号
            phoneLimiter.reset(phone);
            log.info("密码登录成功, uid={}, phone={}", user.getUid(), LogUtils.mask(phone));
            return resp;
        } catch (BusinessException e) {
            // 仅对"密码错误"类失败累计配额；账号禁用、限流自身不再加权（避免被攻击者利用做 DoS）
            if (e.getCode() == ResultCode.PASSWORD_LOGIN_FAILED.getCode()) {
                recordFailureByPhone(phone, clientIp);
            }
            throw e;
        }
    }

    /**
     * 「账号 + 密码」登录 —— account 是 user 的内在唯一标识（{@link User#getAccount()}，8 位纯数字），
     * 与手机号登录、微信登录解耦：任何一种方式注册的用户都会自动获得 account，
     * 只要他们后续设置过密码，即可走此入口登录。
     *
     * <p><b>限流策略</b>（{@link LoginAttemptLimiter}）：
     * <ul>
     *   <li>account 维度：{@value #ACCOUNT_MAX_ATTEMPTS} 次失败 / {@code ATTEMPT_WINDOW}；</li>
     *   <li>IP 维度：与 phone 登录共用同一 {@code ipLimiter}，进一步压低同 IP 总爆破带宽；</li>
     *   <li>超限抛 {@link ResultCode#PASSWORD_LOGIN_RATE_LIMITED}。</li>
     * </ul>
     *
     * <p><b>校验失败</b>一律回 {@link ResultCode#PASSWORD_LOGIN_FAILED}（与手机号密码登录复用同一码），
     * 避免泄露"account 是否存在 / 是否设置过密码"。
     *
     * @param account  用户号（{@link User#getAccount()}）
     * @param password 明文密码
     * @param clientIp 客户端真实 IP；可为 null
     */
    @Transactional
    public LoginResponse loginByAccount(String account, String password, String clientIp) {
        // 1) 限流预检
        if (account != null && !account.isBlank() && !accountLimiter.isAllowed(account)) {
            log.warn("账号登录被限流（account 维度）, account={}", LogUtils.mask(account));
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_RATE_LIMITED);
        }
        if (clientIp != null && !clientIp.isBlank() && !ipLimiter.isAllowed(clientIp)) {
            log.warn("账号登录被限流（IP 维度）, ip={}", LogUtils.mask(clientIp));
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_RATE_LIMITED);
        }

        try {
            User user = userRepository.findByAccount(account)
                    .orElseThrow(() -> {
                        log.info("账号登录失败：account 不存在, account={}", LogUtils.mask(account));
                        return new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
                    });

            if (!Boolean.TRUE.equals(user.getEnabled())) {
                log.warn("账号登录失败：用户已禁用, uid={}", user.getUid());
                throw new BusinessException(ResultCode.USER_DISABLED);
            }

            String hash = user.getPasswordHash();
            if (hash == null || hash.isBlank()) {
                log.info("账号登录失败：未设置密码, uid={}", user.getUid());
                throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
            }
            if (!passwordEncoder.matches(password, hash)) {
                log.info("账号登录失败：密码错误, uid={}", user.getUid());
                throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
            }

            // 校验通过 → 颁 token；loginType 写为 ACCOUNT 表示「最近一次走的是账号密码入口」
            LoginResponse resp = authService.loginExistingUser(user, User.LoginType.ACCOUNT);
            accountLimiter.reset(account);
            log.info("账号登录成功, uid={}, account={}", user.getUid(), LogUtils.mask(account));
            return resp;
        } catch (BusinessException e) {
            if (e.getCode() == ResultCode.PASSWORD_LOGIN_FAILED.getCode()) {
                recordFailureByAccount(account, clientIp);
            }
            throw e;
        }
    }

    /** 累加 phone + IP 双维度失败计数；任一为空 key 时静默跳过该维度。 */
    private void recordFailureByPhone(String phone, String clientIp) {
        if (phone != null && !phone.isBlank()) {
            int after = phoneLimiter.recordFailure(phone);
            if (after >= PHONE_MAX_ATTEMPTS) {
                log.warn("密码登录失败次数达上限（phone 维度）, phone={}, count={}/{}",
                        LogUtils.mask(phone), after, PHONE_MAX_ATTEMPTS);
            }
        }
        recordIpFailure(clientIp);
    }

    /** 累加 account + IP 双维度失败计数。 */
    private void recordFailureByAccount(String account, String clientIp) {
        if (account != null && !account.isBlank()) {
            int after = accountLimiter.recordFailure(account);
            if (after >= ACCOUNT_MAX_ATTEMPTS) {
                log.warn("账号登录失败次数达上限（account 维度）, account={}, count={}/{}",
                        LogUtils.mask(account), after, ACCOUNT_MAX_ATTEMPTS);
            }
        }
        recordIpFailure(clientIp);
    }

    private void recordIpFailure(String clientIp) {
        if (clientIp != null && !clientIp.isBlank()) {
            int after = ipLimiter.recordFailure(clientIp);
            if (after >= IP_MAX_ATTEMPTS) {
                log.warn("密码登录失败次数达上限（IP 维度）, ip={}, count={}/{}",
                        LogUtils.mask(clientIp), after, IP_MAX_ATTEMPTS);
            }
        }
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
