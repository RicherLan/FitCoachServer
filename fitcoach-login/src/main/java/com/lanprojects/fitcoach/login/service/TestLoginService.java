package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内部测试账号登录服务 —— 配合 dev/staging 包的"摇一摇 → 测试账号登录"使用。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>账号 + 密码本质上是真实 user（{@link User.LoginType#TEST}）+ BCrypt hash 密码，
 *       走真实 token 颁发流程，因此 QA 拿到的是完整可用的会话，所有业务接口都能调；</li>
 *   <li>账号由 {@code DataInitializer} 启动时 seed（test_test1/test_test2/test_test3），
 *       新增账号只需改 DataInitializer 即可，本服务无需改动；</li>
 *   <li>双重门禁：客户端 dev/staging 包限定入口 + 服务端 {@link #CONFIG_TEST_LOGIN_ENABLED}
 *       开关；任一关闭都禁用，避免线上被攻击者枚举使用；</li>
 *   <li>所有失败统一抛 {@link ResultCode#PASSWORD_LOGIN_FAILED}（同密码登录），
 *       不暴露"开关是否打开 / 账号是否存在 / 密码是否正确"等具体信号。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestLoginService {

    /**
     * sys_config 中的"测试登录开关"键，默认 false。
     * <p>生产环境必须保持 false，运维通过 admin 后台修改。
     */
    public static final String CONFIG_TEST_LOGIN_ENABLED = "test_login.enabled";

    /**
     * 客户端短账号到 user.uid 的前缀。
     * <p>客户端传 {@code test1}，server 拼出 {@code test_test1} 去查表。
     * 真实 server-issued uid 是 32 位 UUID（去 -），物理上不会撞上 {@code test_*}。
     */
    public static final String TEST_UID_PREFIX = "test_";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SysConfigService sysConfigService;
    private final AuthService authService;

    /**
     * 用账号 + 密码登录测试账号。
     * <p>校验流程：开关 → 找 user → 启用状态 → BCrypt 密码比对 → 颁 token。
     * 失败统一回 {@link ResultCode#PASSWORD_LOGIN_FAILED}。
     */
    @Transactional
    public LoginResponse login(String account, String password) {
        // 1) 全局开关校验：未启用 → 当作"登录失败"，不暴露具体原因
        if (!sysConfigService.getBoolValue(CONFIG_TEST_LOGIN_ENABLED, false)) {
            log.warn("test login 被拒：服务端开关未启用, account={}", account);
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }

        String uid = TEST_UID_PREFIX + account;
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> {
                    log.info("test login 失败：账号不存在, account={}", account);
                    return new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
                });

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("test login 失败：账号已禁用, uid={}", uid);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 校验登录类型必须是 TEST —— 防御性兜底，防止有人用 server-issued uid 撞名
        if (user.getLoginType() != User.LoginType.TEST) {
            log.warn("test login 拒绝：用户登录类型非 TEST, uid={}, loginType={}",
                    uid, user.getLoginType());
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }

        String hash = user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            log.warn("test login 失败：账号未设置密码, uid={}", uid);
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(password, hash)) {
            log.info("test login 失败：密码错误, uid={}", uid);
            throw new BusinessException(ResultCode.PASSWORD_LOGIN_FAILED);
        }

        log.info("test login 成功, uid={}", uid);
        return authService.loginExistingUser(user);
    }
}
