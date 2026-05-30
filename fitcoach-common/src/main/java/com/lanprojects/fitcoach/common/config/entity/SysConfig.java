package com.lanprojects.fitcoach.common.config.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 系统配置表 — 存储动态配置项（如微信 AppId、AppSecret 等）
 * <p>
 * 配置存储在数据库中，管理员可通过后台管理平台修改，
 * 避免敏感信息硬编码在代码或配置文件中。
 * <p>
 * 敏感字段（如 AppSecret）通过 {@link #encrypted}=true 标记，
 * 由 {@code SysConfigService} 在写入/读取时自动加解密，
 * 数据库里只保留密文。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_config", indexes = {
        // config_key 已有 unique 约束（@Column unique=true）— 单列唯一 + 等值查询足够
        // 按 config_group 分组查询（admin 后台分组展示用），加索引
        @Index(name = "idx_sys_config_group", columnList = "config_group")
})
public class SysConfig extends BaseEntity {

    /**
     * 配置键（唯一），如 "wechat.app_id"
     */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 配置值。
     * 当 {@link #encrypted}=true 时，存储的是 AES 加密后的 base64 密文。
     */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /**
     * 配置分组，如 "wechat"、"jwt"、"system"
     */
    @Column(name = "config_group", length = 50)
    private String configGroup;

    /**
     * 配置描述
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * 是否加密存储。
     * <p>true=configValue 是密文，读取需自动解密；false=明文。</p>
     */
    @Column(name = "encrypted", nullable = false)
    private Boolean encrypted = false;

    public SysConfig(String configKey, String configValue, String configGroup, String description) {
        this(configKey, configValue, configGroup, description, false);
    }

    public SysConfig(String configKey, String configValue, String configGroup, String description, boolean encrypted) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configGroup = configGroup;
        this.description = description;
        this.encrypted = encrypted;
    }
}
