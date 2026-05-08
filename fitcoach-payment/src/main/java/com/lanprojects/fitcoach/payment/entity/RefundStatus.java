package com.lanprojects.fitcoach.payment.entity;

/**
 * 退款子状态。独立于 {@link OrderStatus}，避免主状态机被退款细节污染。
 */
public enum RefundStatus {
    /** 无退款（默认） */
    NONE,

    /** 退款已发起，等通道处理 */
    PENDING,

    /** 退款已完成 */
    COMPLETED,

    /** 退款失败（人工介入） */
    FAILED
}
