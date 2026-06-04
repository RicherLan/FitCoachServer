package com.lanprojects.fitcoach.config;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.common.config.service.ConfigCryptoService;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.service.AccountGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 数据初始化器 — 应用首次启动时写入默认配置 + 历史数据 migration
 * <p>
 * 配置只在 key 不存在时插入，不会覆盖已有值。
 * 管理员后续通过后台管理平台修改这些配置。
 * <p>
 * <b>安全相关：</b>
 * <ul>
 *   <li>JWT 密钥不再硬编码，每个新部署的实例随机生成 48 字节并入库；</li>
 *   <li>微信 AppSecret 标记为 encrypted=true，由 SysConfigService 自动加解密；</li>
 *   <li>Access token 默认 2 小时；refresh token 默认 7 天。</li>
 * </ul>
 *
 * <b>账号体系 migration（一次性）：</b>
 * <ul>
 *   <li>给所有 {@code account == null} 的老用户生成 8 位纯数字 account；</li>
 *   <li>把 {@code loginType == TEST} 的历史用户改写为 {@code ACCOUNT}；</li>
 *   <li>给所有 {@code registrationSource == null} 的老用户统一打上 {@code LEGACY} 标签。</li>
 * </ul>
 * 该 migration 幂等：再次启动跳过已补齐的行。
 *
 * <p>历史的「test_login.enabled 系统配置」+「默认 test1/test2/test3 seed 账号」均已在
 * 账号体系重构中下线 —— 现在所有内部 / QA 账号统一通过 admin 后台「用户管理 → 创建用户」
 * 入口生成，account 由 server 端 {@code AccountGenerator} 自动分配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysConfigRepository sysConfigRepository;
    private final ConfigCryptoService configCryptoService;
    private final UserRepository userRepository;
    private final AccountGenerator accountGenerator;

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

        if (inserted > 0) {
            log.info("初始化完成，新增 {} 项配置", inserted);
        } else {
            log.info("配置已存在，跳过初始化");
        }

        // ====== 历史用户数据 migration ======
        // 一次性把老 user 的 account / loginType=TEST / registrationSource 三个字段补齐。
        // 幂等：再次启动会跳过已补齐的行。
        int migrated = migrateLegacyUsers();
        if (migrated > 0) {
            log.info("[migration] 补齐历史用户字段, count={}", migrated);
        } else {
            log.info("[migration] 历史用户均已补齐，跳过 migration");
        }
    }

    /**
     * 对所有缺失新字段的历史用户做一次性 migration。
     * <p>策略：
     * <ul>
     *   <li>account == null → 调 {@link AccountGenerator#generateUnique()} 生成；</li>
     *   <li>loginType == TEST → 改为 {@link User.LoginType#ACCOUNT}；</li>
     *   <li>registrationSource == null → 写入 {@link User.RegistrationSource#LEGACY}。</li>
     * </ul>
     * 一次启动可能改不完（如生成 account 时唯一索引冲突），下次启动会继续补；
     * 由 {@link AccountGenerator} 内置 16 次重试和 unique 索引保底，正常不会有死循环。
     *
     * @return 本次实际写库的用户数（仅统计真正发生变更的行）
     */
    @Transactional
    protected int migrateLegacyUsers() {
        List<User> all = userRepository.findAll();
        int count = 0;
        for (User user : all) {
            boolean dirty = false;

            // 兼容历史 TEST 数据：统一改写为 ACCOUNT；保留原 passwordHash，仍可用账号 + 密码登录
            if (user.getLoginType() == User.LoginType.TEST) {
                user.setLoginType(User.LoginType.ACCOUNT);
                dirty = true;
            }

            // 注册来源缺失 → 统一打 LEGACY 标签，便于运营区分历史用户
            if (user.getRegistrationSource() == null) {
                user.setRegistrationSource(User.RegistrationSource.LEGACY);
                dirty = true;
            }

            // 关键：account 是新引入的"内在唯一标识"，所有老用户必须补
            if (user.getAccount() == null || user.getAccount().isBlank()) {
                try {
                    user.setAccount(accountGenerator.generateUnique());
                    dirty = true;
                } catch (RuntimeException e) {
                    // 单个 user 生成失败不阻塞整体 migration —— 下次启动重试
                    log.warn("[migration] 给用户生成 account 失败, uid={}, msg={}",
                            user.getUid(), e.getMessage());
                }
            }

            if (dirty) {
                userRepository.save(user);
                count++;
            }
        }
        return count;
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
