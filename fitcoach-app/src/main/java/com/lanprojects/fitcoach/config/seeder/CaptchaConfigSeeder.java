package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.common.config.service.ConfigCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 验证码相关 SysConfig 默认值播种。
 *
 * <p>从 application.yml 读取 captcha.* 配置作为初始值，写入 sys_config 表。
 * 之后 CaptchaService 改从 sys_config 读取（通过 SysConfigService），
 * 管理员可在后台管理平台动态修改，无需重启服务。
 *
 * <p>只在配置项不存在时插入，不会覆盖已有值。
 */
@Slf4j
@Order(21)
@Component
@RequiredArgsConstructor
public class CaptchaConfigSeeder implements CommandLineRunner {

    /** 配置 key 常量 */
    public static final String KEY_CAPTCHA_ENABLED = "captcha.enabled";
    public static final String KEY_CAPTCHA_APP_ID = "captcha.app_id";
    public static final String KEY_CAPTCHA_APP_SECRET_KEY = "captcha.app_secret_key";

    private final SysConfigRepository sysConfigRepository;
    private final ConfigCryptoService configCryptoService;

    @Value("${captcha.enabled:false}")
    private boolean captchaEnabled;

    @Value("${captcha.app-id:}")
    private String captchaAppId;

    @Value("${captcha.app-secret-key:}")
    private String captchaAppSecretKey;

    @Override
    public void run(String... args) {
        int inserted = 0;

        // 验证码总开关
        inserted += ensureExists(new SysConfig(
                KEY_CAPTCHA_ENABLED, String.valueOf(captchaEnabled), "captcha",
                "腾讯行为验证码总开关（true=启用，false=跳过校验）"));

        // CaptchaAppId — 明文存储
        inserted += ensureExists(new SysConfig(
                KEY_CAPTCHA_APP_ID, captchaAppId, "captcha",
                "腾讯验证码 CaptchaAppId（控制台获取）"));

        // AppSecretKey — 加密存储
        String encryptedSecret = configCryptoService.encrypt(captchaAppSecretKey);
        inserted += ensureExists(new SysConfig(
                KEY_CAPTCHA_APP_SECRET_KEY, encryptedSecret, "captcha",
                "腾讯验证码 AppSecretKey（加密存储，后台管理平台修改）", true));

        if (inserted > 0) {
            log.info("[seeder] 验证码配置初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensureExists(SysConfig config) {
        if (sysConfigRepository.findByConfigKey(config.getConfigKey()).isEmpty()) {
            sysConfigRepository.save(config);
            return 1;
        }
        return 0;
    }
}
