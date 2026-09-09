package com.lanprojects.fitcoach.payment.provider;

/**
 * Provider 退款结果（Provider → RefundService）。
 *
 * <p>不同通道退款的完成时机不同，用 {@link #synchronouslyCompleted} 区分：
 * <ul>
 *   <li>有些通道同步返回即代表退款成功（可直接把退款单置 COMPLETED）；</li>
 *   <li>有些通道（如微信）退款是「受理成功 + 异步结果通知」，此时 {@code success=true} 但
 *       {@code synchronouslyCompleted=false}，退款单先留 PENDING，等退款回调再置 COMPLETED。</li>
 * </ul>
 *
 * @param channelRefundId        通道侧退款单号（微信 refund_id 等），落库便于对账
 * @param success                通道是否受理成功
 * @param synchronouslyCompleted 是否同步即完成（true→直接 COMPLETED；false→受理成功待异步回调）
 * @param rawPayload             通道返回原文（落 refund_order.extra_json 审计）
 */
public record RefundResult(
        String channelRefundId,
        boolean success,
        boolean synchronouslyCompleted,
        String rawPayload
) {
}
