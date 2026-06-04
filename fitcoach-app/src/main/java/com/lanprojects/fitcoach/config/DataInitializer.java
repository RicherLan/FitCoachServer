package com.lanprojects.fitcoach.config;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.common.config.service.ConfigCryptoService;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.service.TestLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 数据初始化器 — 应用首次启动时写入默认配置
 * <p>
 * 只在配置项不存在时插入，不会覆盖已有值。
 * 管理员后续通过后台管理平台修改这些配置。
 * <p>
 * <b>安全相关：</b>
 * <ul>
 *   <li>JWT 密钥不再硬编码，每个新部署的实例随机生成 48 字节并入库；</li>
 *   <li>微信 AppSecret 标记为 encrypted=true，由 SysConfigService 自动加解密；</li>
 *   <li>Access token 默认 2 小时；refresh token 默认 7 天。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** 默认 seed 出来的 3 个测试账号，与客户端 {@code TEST_ACCOUNTS} 一一对应 */
    private static final List<String> DEFAULT_TEST_ACCOUNTS = List.of("test1", "test2", "test3");
    /** 测试账号的默认密码（首次 seed 时写入，后续可通过 admin 后台改） */
    private static final String DEFAULT_TEST_PASSWORD = "123456";

    private final SysConfigRepository sysConfigRepository;
    private final ConfigCryptoService configCryptoService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void run(String... args) {
        int inserted = 0;
        // ====== 微信配置（敏感字段加密存储） ======
        inserted += ensureExists(new SysConfig(
                "wechat.app_id", "", "wechat", "微信开放平台 AppID"));
        // AppSecret：encrypted=true，初始空值（运维通过管理平台填入；写入时自动加密）
        inserted += ensureExists(new SysConfig(
                "wechat.app_secret", configCryptoService.encrypt(""),
                "wechat", "微信开放平台 AppSecret（加密存储，勿直接修改 DB）", true));

        // ====== JWT 配置 ======
        // jwt.secret 不再硬编码：每实例首次启动随机生成 48 字节（base64 后 ~64 字符）
        inserted += ensureExists(new SysConfig(
                "jwt.secret", generateJwtSecret(),
                "jwt", "JWT 签名密钥（首次启动自动生成，请妥善保管）"));
        // 默认 access token 2h
        inserted += ensureExists(new SysConfig(
                "jwt.expire_hours", "2",
                "jwt", "Access token 过期时间（小时），默认 2h"));
        // 默认 refresh token 7d
        inserted += ensureExists(new SysConfig(
                "jwt.refresh_expire_hours", "168",
                "jwt", "Refresh token 过期时间（小时），默认 7 天"));

        // ====== 内部测试账号登录开关 ======
        // 默认 false（关闭）；开发/staging 环境运维在 admin 后台改 true 才能让客户端"测试账号登录"工作。
        // 关键：生产环境必须保持 false，防止攻击者枚举 test1/test2/test3 走密码爆破。
        inserted += ensureExists(new SysConfig(
                TestLoginService.CONFIG_TEST_LOGIN_ENABLED, "false",
                "test_login", "内部测试账号登录开关（dev/staging 用，生产必须 false）"));

        if (inserted > 0) {
            log.info("初始化完成，新增 {} 项配置", inserted);
        } else {
            log.info("配置已存在，跳过初始化");
        }

        // ====== 测试账号 seed ======
        // 即使开关关着，账号本身也提前 seed，让"打开开关即可登录"做到秒级生效；
        // 关闭开关时账号存在也无法被外部利用 —— TestLoginService 在最前面就拦住了。
        int seededAccounts = seedTestAccounts();
        if (seededAccounts > 0) {
            log.info("测试账号初始化完成，新增 {} 个", seededAccounts);
        } else {
            log.info("测试账号已存在，跳过初始化");
        }
    }

    /**
     * 按 {@link #DEFAULT_TEST_ACCOUNTS} 列表 seed 测试账号到 user 表。
     * <p>uid 形如 {@code test_test1}，对应 {@link TestLoginService#TEST_UID_PREFIX} + 短账号名。
     * <p>已存在的账号跳过（避免覆盖管理员后续手动改的密码），返回新插入条数。
     */
    private int seedTestAccounts() {
        int inserted = 0;
        for (String account : DEFAULT_TEST_ACCOUNTS) {
            String uid = TestLoginService.TEST_UID_PREFIX + account;
            if (userRepository.findByUid(uid).isPresent()) {
                continue;
            }
            User user = new User();
            user.setUid(uid);
            user.setNickname("测试账号 " + account.replace("test", ""));
            user.setLoginType(User.LoginType.TEST);
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_TEST_PASSWORD));
            user.setEnabled(true);
            user.setGender(0);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("初始化测试账号: uid={}（默认密码：{}）", uid, DEFAULT_TEST_PASSWORD);
            inserted++;
        }
        return inserted;
    }

    private int ensureExists(SysConfig config) {
        if (sysConfigRepository.findByConfigKey(config.getConfigKey()).isEmpty()) {
            sysConfigRepository.save(config);
            log.info("初始化配置: {} = {}", config.getConfigKey(),
                    isSensitive(config) ? "***" : config.getConfigValue());
            return 1;
        }
        return 0;
    }

    private boolean isSensitive(SysConfig config) {
        return Boolean.TRUE.equals(config.getEncrypted())
                || config.getConfigKey().contains("secret");
    }

    /**
     * 生成 48 字节随机 → base64 编码（约 64 字符），满足 HMAC-SHA256 32 字节最低长度要求且更安全。
     */
    private String generateJwtSecret() {
        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        return Base64.getEncoder().encodeToString(random);
    }
}
