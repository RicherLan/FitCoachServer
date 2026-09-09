package com.lanprojects.fitcoach.payment.provider;

/**
 * Provider 退款请求（RefundService → Provider 契约）。仅 ACTIVE 主动退款通道（微信 / 支付宝 / Google / Stripe）使用；
 * PASSIVE 通道（Apple / 线下）不经过 Provider.refund。
 *
 * @param refundNo             退款单号（作为通道侧 out_refund_no，保证退款回调能反查）
 * @param orderId              原订单号（通道侧 out_trade_no）
 * @param channelTransactionId 原支付的通道凭证号（微信 transaction_id 等，部分通道退款需要）
 * @param refundCents          本次退款金额（最小货币单位）
 * @param totalAmountCents     订单原总金额（微信退款 API 必传 amount.total）
 * @param currency             币种（CNY / USD）
 * @param reason               退款原因
 */
public record RefundRequest(
        String refundNo,
        String orderId,
        String channelTransactionId,
        int refundCents,
        int totalAmountCents,
        String currency,
        String reason
) {
}
