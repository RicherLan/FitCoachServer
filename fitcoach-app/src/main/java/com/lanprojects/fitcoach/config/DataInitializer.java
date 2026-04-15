package com.lanprojects.fitcoach.config;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化器 — 应用首次启动时写入默认配置
 * <p>
 * 只在配置项不存在时插入，不会覆盖已有值。
 * 管理员后续通过后台管理平台修改这些配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysConfigRepository sysConfigRepository;

    @Override
    public void run(String... args) {
        List<SysConfig> defaults = List.of(
                // ====== 微信配置 ======
                new SysConfig("wechat.app_id", "",
                        "wechat", "微信开放平台 AppID"),
                new SysConfig("wechat.app_secret", "",
                        "wechat", "微信开放平台 AppSecret（敏感，勿泄露）"),

                // ====== JWT 配置 ======
                new SysConfig("jwt.secret", "FitCoach2026SecretKeyForJwtToken!!",
                        "jwt", "JWT 签名密钥（至少 32 字符）"),
                new SysConfig("jwt.expire_hours", "168",
                        "jwt", "JWT 过期时间（小时），默认 7 天")
        );

        int inserted = 0;
        for (SysConfig config : defaults) {
            if (sysConfigRepository.findByConfigKey(config.getConfigKey()).isEmpty()) {
                sysConfigRepository.save(config);
                inserted++;
                log.info("初始化配置: {} = {}", config.getConfigKey(),
                        config.getConfigKey().contains("secret") ? "***" : config.getConfigValue());
            }
        }

        if (inserted > 0) {
            log.info("初始化完成，新增 {} 项配置", inserted);
        } else {
            log.info("配置已存在，跳过初始化");
        }
    }
}
