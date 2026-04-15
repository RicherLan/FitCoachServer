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
 * 系统配置服务 — 从数据库读取配置，带内存缓存
 * <p>
 * 所有动态配置（如微信 AppId/AppSecret）都通过此服务获取，
 * 避免硬编码。后台管理平台修改配置后调用 {@link #refreshCache()} 刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigRepository sysConfigRepository;

    /**
     * 配置缓存：key → value
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 缓存是否已初始化
     */
    private volatile boolean cacheLoaded = false;

    // ====== 读取配置 ======

    /**
     * 获取配置值
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
            log.warn("配置项 {} 的值 '{}' 不是有效的整数，使用默认值 {}", key, value, defaultValue);
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

    /**
     * 确保缓存已加载（延迟加载）
     */
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
     * 从数据库加载所有启用的配置到缓存
     */
    private void loadCache() {
        try {
            List<SysConfig> configs = sysConfigRepository.findByEnabledTrue();
            for (SysConfig config : configs) {
                cache.put(config.getConfigKey(), config.getConfigValue());
            }
            cacheLoaded = true;
            log.info("加载系统配置 {} 项", configs.size());
        } catch (Exception e) {
            log.error("加载系统配置失败", e);
        }
    }

    // ====== 写入配置 ======

    /**
     * 设置配置值（存入数据库 + 更新缓存）
     */
    public void setValue(String key, String value) {
        SysConfig config = sysConfigRepository.findByConfigKey(key)
                .orElse(new SysConfig(key, value, null, null));
        config.setConfigValue(value);
        sysConfigRepository.save(config);
        cache.put(key, value);
    }
}
