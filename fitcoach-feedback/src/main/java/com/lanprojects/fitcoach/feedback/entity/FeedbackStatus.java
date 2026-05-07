package com.lanprojects.fitcoach.feedback.entity;

/**
 * 反馈处理状态机。
 * <p>
 * 状态流转（管理员后台驱动）：
 * <pre>
 *   PENDING (待处理，新提交时默认)
 *      ├──► PROCESSING (处理中)
 *      │       ├──► RESOLVED (已解决)
 *      │       └──► IGNORED (已忽略)
 *      ├──► RESOLVED   (跳过 PROCESSING 直接结案)
 *      └──► IGNORED    (无需处理 / 重复 / 无效)
 * </pre>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>状态可逆 — 管理员误操作可以从 RESOLVED 改回 PROCESSING；不强制单向流转，</li>
 *   <li>{@code @Enumerated(EnumType.STRING)} 入库 — 数据库可读、加新状态值无需迁移。</li>
 * </ul>
 */
public enum FeedbackStatus {
    /** 待处理（新提交） */
    PENDING,
    /** 处理中（已认领） */
    PROCESSING,
    /** 已解决 */
    RESOLVED,
    /** 已忽略（无效 / 重复 / 不予处理） */
    IGNORED
}
