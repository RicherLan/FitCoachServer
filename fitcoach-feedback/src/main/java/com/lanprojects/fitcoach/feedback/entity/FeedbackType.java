package com.lanprojects.fitcoach.feedback.entity;

/**
 * 反馈类型枚举。
 * <p>新增类型只需在此追加 + 客户端同步加 chip，
 * 数据库列已用 {@code @Enumerated(EnumType.STRING)} 兼容新值，无需 DDL 变更。
 */
public enum FeedbackType {

    /** 功能建议：希望产品做某些事 */
    SUGGESTION,

    /** 体验问题：使用过程中卡顿 / 难用 / 不舒服 */
    EXPERIENCE,

    /** 其他：以上分类涵盖不到的内容 */
    OTHER
}
