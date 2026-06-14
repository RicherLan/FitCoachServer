package com.lanprojects.fitcoach.notification.dto;

import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 系统通知下发给客户端的 DTO（出现在 {@code /api/client/poll} 响应的 {@code systemNotification} 字段下）。
 *
 * <p>故意只下发"展示 + 跳转"必要字段：
 * <ul>
 *   <li>不带 status / targetAudience / targetUids / effectiveAt / expireAt 等"投放策略"字段，
 *       这些是 server 内部判定的依据，客户端无须感知；</li>
 *   <li>{@code id} 用于客户端本地 seenIds 去重；</li>
 *   <li>secondary 按钮可空 —— null 时客户端只渲染主按钮。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemNotificationClientDto {

    /** 通知 ID，客户端拿这个去重（存在 storage seenIds Set 里） */
    private Long id;

    /** 弹窗标题 */
    private String title;

    /** 弹窗正文 */
    private String message;

    /** 主按钮（必有） */
    private NotificationButton primaryButton;

    /** 副按钮（可空，null 时客户端只渲染主按钮） */
    private NotificationButton secondaryButton;

    /**
     * 从 entity 构造客户端 DTO。
     * <p>主按钮 url 字段一定存在（即便为空字符串）；副按钮 text 为空时整个对象返回 null。
     */
    public static SystemNotificationClientDto from(SystemNotificationEntity e) {
        NotificationButton primary = new NotificationButton(
                e.getPrimaryButtonText(),
                e.getPrimaryButtonUrl() == null ? "" : e.getPrimaryButtonUrl());

        NotificationButton secondary = null;
        if (e.getSecondaryButtonText() != null && !e.getSecondaryButtonText().isBlank()) {
            secondary = new NotificationButton(
                    e.getSecondaryButtonText(),
                    e.getSecondaryButtonUrl() == null ? "" : e.getSecondaryButtonUrl());
        }

        return new SystemNotificationClientDto(e.getId(), e.getTitle(), e.getMessage(), primary, secondary);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationButton {
        /** 按钮文案 */
        private String text;
        /** 点击跳转 URL（空字符串表示仅关闭弹窗） */
        private String url;
    }
}
