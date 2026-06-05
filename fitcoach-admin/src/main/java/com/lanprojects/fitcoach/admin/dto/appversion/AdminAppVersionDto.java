package com.lanprojects.fitcoach.admin.dto.appversion;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端 App 版本 DTO（与 App 端 LatestVersionDTO 区分：含运营字段如 isPublished / 时间戳 / 文件信息）。
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

    // ====== 安装包信息 ======

    /** 安装包下载 URL（null 表示未上传） */
    private String packageUrl;
    /** 安装包大小（字节） */
    private Long packageSize;
    /** 安装包 MD5 */
    private String packageMd5;
    /** 安装包原始文件名 */
    private String packageFileName;

    // ====== Mapping 文件信息（仅 Android） ======

    /** Mapping 文件下载 URL */
    private String mappingUrl;
    /** Mapping 文件大小（字节） */
    private Long mappingSize;
    /** Mapping 文件 MD5 */
    private String mappingMd5;
    /** Mapping 原始文件名 */
    private String mappingFileName;

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
                .packageUrl(v.getPackageUrl())
                .packageSize(v.getPackageSize())
                .packageMd5(v.getPackageMd5())
                .packageFileName(v.getPackageFileName())
                .mappingUrl(v.getMappingUrl())
                .mappingSize(v.getMappingSize())
                .mappingMd5(v.getMappingMd5())
                .mappingFileName(v.getMappingFileName())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
