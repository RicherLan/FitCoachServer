package com.lanprojects.fitcoach.admin.dto.appversion;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端 App 版本 DTO（与 App 端 LatestVersionDTO 区分：含运营字段如 isPublished / 时间戳）。
 *
 * <p>该 DTO 与 {@link AppVersionEntity} 字段一一对应，admin 列表 / 详情 / 创建/更新返回值都用它。
 */
@Data
@Builder
public class AdminAppVersionDto {

    private Long id;

    /** 客户端平台：android / ios */
    private String platform;

    /** 展示版本号，如 "1.2.3" */
    private String versionName;

    /** 数值版本号，如 1002003 */
    private Integer versionCode;

    /** 更新说明（admin 录入时支持换行） */
    private String releaseNotes;

    /** 应用商店跳转链接 */
    private String downloadUrl;

    /** 是否强制升级 */
    private Boolean isForce;

    /** 是否已发布（false = 草稿，对 App 端不可见） */
    private Boolean isPublished;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminAppVersionDto from(AppVersionEntity v) {
        return AdminAppVersionDto.builder()
                .id(v.getId())
                .platform(v.getPlatform())
                .versionName(v.getVersionName())
                .versionCode(v.getVersionCode())
                .releaseNotes(v.getReleaseNotes())
                .downloadUrl(v.getDownloadUrl())
                .isForce(Boolean.TRUE.equals(v.getIsForce()))
                .isPublished(Boolean.TRUE.equals(v.getIsPublished()))
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
