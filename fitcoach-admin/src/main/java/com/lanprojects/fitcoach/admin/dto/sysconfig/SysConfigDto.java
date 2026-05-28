package com.lanprojects.fitcoach.admin.dto.sysconfig;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 系统配置 — 管理后台列表/详情 DTO。
 * <p>
 * 加密字段（{@code encrypted=true}）的 {@code configValue} 做脱敏处理（显示 ***），
 * 防止管理员在浏览器中泄露密钥。仅在编辑提交时传入新值才覆盖。
 */
@Getter
@Builder
public class SysConfigDto {

    private Long id;
    private String configKey;
    /** 加密配置显示 "******"，明文配置显示原值 */
    private String configValue;
    private String configGroup;
    private String description;
    private Boolean enabled;
    private Boolean encrypted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转 DTO，加密字段自动脱敏。
     */
    public static SysConfigDto from(SysConfig entity) {
        return SysConfigDto.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(Boolean.TRUE.equals(entity.getEncrypted()) ? "******" : entity.getConfigValue())
                .configGroup(entity.getConfigGroup())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .encrypted(entity.getEncrypted())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
