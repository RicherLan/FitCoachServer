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
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_config")
public class SysConfig extends BaseEntity {

    /**
     * 配置键（唯一），如 "wechat.app_id"
     */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 配置值，如 "wx1234567890abcdef"
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

    public SysConfig(String configKey, String configValue, String configGroup, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configGroup = configGroup;
        this.description = description;
    }
}
