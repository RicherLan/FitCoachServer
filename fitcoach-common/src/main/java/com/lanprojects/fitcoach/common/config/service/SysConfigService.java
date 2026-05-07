package com.lanprojects.fitcoach.common.config.service;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务 — 从数据库读取配置，带内存缓存。
 * <p>
 * 所有动态配置（如微信 AppId/AppSecret）都通过此服务获取，避免硬编码。
 * 标记为 encrypted 的配置在读写时自动加解密，缓存里存的是<b>明文</b>。
 * 后台管理平台修改配置后调用 {@link #refreshCache()} 刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigRepository sysConfigRepository;
    private final ConfigCryptoService configCryptoService;

    /**
     * 配置缓存：key → value（明文）
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 缓存是否已初始化
     */
    private volatile boolean cacheLoaded = false;

    // ====== 读取配置 ======

    /**
     * 获取配置值（已自动解密，调用方拿到的是明文）
     */
    public String getValue(String key) {
        ensureCacheLoaded();
        return cache.get(key);
    }

    /**
     * 获取配置值，不存在时返回默认值
     */
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取配置值（整数）
     */
    public int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值不是有效的整数，使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取配置值（布尔）
     */
    public boolean getBoolValue(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    // ====== 缓存管理 ======

    /**
     * 刷新配置缓存（后台管理平台修改配置后调用）
     */
    public void refreshCache() {
        log.info("刷新系统配置缓存...");
        cache.clear();
        loadCache();
        log.info("系统配置缓存刷新完成，共 {} 项", cache.size());
    }

    private void ensureCacheLoaded() {
        if (!cacheLoaded) {
            synchronized (this) {
                if (!cacheLoaded) {
                    loadCache();
                }
            }
        }
    }

    /**
     * 从数据库加载所有启用的配置到缓存（加密项自动解密）
     */
    private void loadCache() {
        try {
            List<SysConfig> configs = sysConfigRepository.findByEnabledTrue();
            for (SysConfig config : configs) {
                String value = config.getConfigValue();
                if (Boolean.TRUE.equals(config.getEncrypted())) {
                    try {
                        value = configCryptoService.decrypt(value);
                    } catch (Exception e) {
                        log.error("配置项 {} 解密失败，跳过加载", config.getConfigKey());
                        continue;
                    }
                }
                cache.put(config.getConfigKey(), value);
            }
            cacheLoaded = true;
            log.info("加载系统配置 {} 项", configs.size());
        } catch (Exception e) {
            log.error("加载系统配置失败: {}", e.getClass().getSimpleName());
        }
    }

    // ====== 写入配置 ======

    /**
     * 设置明文配置值（存入数据库 + 更新缓存）。
     * 已存在的配置如果原本是 encrypted=true，则继续加密；否则保持明文。
     */
    public void setValue(String key, String value) {
        SysConfig config = sysConfigRepository.findByConfigKey(key)
                .orElse(new SysConfig(key, value, null, null, false));
        if (Boolean.TRUE.equals(config.getEncrypted())) {
            config.setConfigValue(configCryptoService.encrypt(value));
        } else {
            config.setConfigValue(value);
        }
        sysConfigRepository.save(config);
        cache.put(key, value);
    }

    /**
     * 设置加密配置值（自动加密后入库 + 缓存明文）。
     */
    public void setEncryptedValue(String key, String plaintextValue) {
        SysConfig config = sysConfigRepository.findByConfigKey(key)
                .orElse(new SysConfig(key, null, null, null, true));
        config.setEncrypted(true);
        config.setConfigValue(configCryptoService.encrypt(plaintextValue));
        sysConfigRepository.save(config);
        cache.put(key, plaintextValue);
    }
}
