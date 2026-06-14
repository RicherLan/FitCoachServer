package com.lanprojects.fitcoach.admin.dto.notification;

import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.entity.TargetAudience;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端系统通知 DTO（列表 / 详情 / 创建/更新返回值通用）。
 *
 * <p>与客户端 {@code SystemNotificationClientDto} 的差异：本 DTO 多了运营字段
 * （status / targetAudience / targetUids / effectiveAt / expireAt / createdBy 等），
 * admin 列表页直接展示。
 */
@Data
@Builder
public class AdminSystemNotificationDto {

    private Long id;
    private String title;
    private String message;

    private String primaryButtonText;
    private String primaryButtonUrl;
    private String secondaryButtonText;
    private String secondaryButtonUrl;

    /** 投放对象类型：ALL / SPECIFIC_USERS */
    private TargetAudience targetAudience;
    /** 投放用户 uid 列表（CSV）；ALL 时为 null */
    private String targetUids;

    /** 投放平台（CSV，如 "android,ios"）；null = 不限平台 */
    private String platforms;

    /** 最小 versionCode（含）；null = 不限下界 */
    private Integer minVersionCode;

    /** 最大 versionCode（含）；null = 不限上界 */
    private Integer maxVersionCode;

    private LocalDateTime effectiveAt;
    private LocalDateTime expireAt;

    /** 发布状态：DRAFT / PUBLISHED / ARCHIVED */
    private NotificationStatus status;

    /** 创建者 admin 用户名（审计用） */
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminSystemNotificationDto from(SystemNotificationEntity e) {
        return AdminSystemNotificationDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .message(e.getMessage())
                .primaryButtonText(e.getPrimaryButtonText())
                .primaryButtonUrl(e.getPrimaryButtonUrl())
                .secondaryButtonText(e.getSecondaryButtonText())
                .secondaryButtonUrl(e.getSecondaryButtonUrl())
                .targetAudience(e.getTargetAudience())
                .targetUids(e.getTargetUids())
                .platforms(e.getPlatforms())
                .minVersionCode(e.getMinVersionCode())
                .maxVersionCode(e.getMaxVersionCode())
                .effectiveAt(e.getEffectiveAt())
                .expireAt(e.getExpireAt())
                .status(e.getStatus())
                .createdBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
