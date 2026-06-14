package com.lanprojects.fitcoach.notification.entity;

/**
 * 系统通知发布状态。
 *
 * <ul>
 *   <li>{@link #DRAFT} —— 草稿，admin 后台可见但不会下发给客户端</li>
 *   <li>{@link #PUBLISHED} —— 已发布，符合 effective_at &lt;= now &lt; expire_at 时会被客户端拉到</li>
 *   <li>{@link #ARCHIVED} —— 已归档（手动下架），客户端永远拉不到；与 DRAFT 的区别仅在 admin 列表语义上</li>
 * </ul>
 *
 * <p>不做"自动归档"：到期后通知不会被自动改成 ARCHIVED，仅靠 service 查询时按
 * {@code now &lt; expire_at} 过滤。这样 admin 可以查到所有历史通知，过期与否一目了然。
 */
public enum NotificationStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
}
