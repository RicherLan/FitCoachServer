package com.lanprojects.fitcoach.payment.entity;

/**
 * 支付订单状态机。
 *
 * <pre>
 * PENDING ──创建后等用户支付──┐
 *    │                       │
 *    │ 用户支付成功 (回调)    │ 用户取消 / 超时
 *    ↓                       ↓
 *  PAID ──申请退款──→ 退款流程   CLOSED
 *    │                  ↑
 *    │   全额退款完成     │
 *    └──────────→ REFUNDED
 *
 *   FAILED：通道返回失败（极少进入，多数失败留在 PENDING + closedAt）
 * </pre>
 *
 * <p>注意：refund 流程的细分状态由 {@link RefundStatus} 维护（NONE/PENDING/COMPLETED/FAILED），
 * 主状态停在 {@code PAID} 直到全额退款完成才切到 {@code REFUNDED}。
 */
public enum OrderStatus {
    /** 已下单，等待支付（未付款不会触发会员激活） */
    PENDING,

    /** 已支付（触发 PaymentSucceededEvent，会员模块据此激活） */
    PAID,

    /** 已退款（全额退款完成后才进入此状态；部分退款仍处于 PAID + RefundStatus.PENDING/COMPLETED 视情况） */
    REFUNDED,

    /** 通道返回明确失败（如银行拒付） */
    FAILED,

    /** 订单关闭：超时未付 / 用户取消 / 重复创建被覆盖 */
    CLOSED
}
