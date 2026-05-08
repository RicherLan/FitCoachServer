package com.lanprojects.fitcoach.payment.provider;

import java.util.Map;

/**
 * Provider 创建订单的结果（Provider → PaymentService → 客户端）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code prepayId}：通道侧返回的预支付凭证（微信 prepay_id）。Apple IAP 没有这个概念；</li>
 *   <li>{@code clientPayload}：传给客户端 SDK 的拉起参数 map。</li>
 *   <li>{@code immediatelyPaid}：true 表示该通道下单即视为支付完成（Mock 通道用），
 *       PaymentService 据此立即触发会员激活流程。</li>
 * </ul>
 *
 * <p>客户端拿 {@code clientPayload} 后按对应 SDK 调用（微信走 WeChat SDK，Apple 走 StoreKit）。
 * 不同通道的 payload 字段不同，所以用 {@code Map<String, Object>} 而不强类型。
 *
 * @param prepayId         通道侧预支付凭证（可空）
 * @param clientPayload    客户端拉起支付所需的参数（不可空，至少含 channel 字段）
 * @param immediatelyPaid  下单即支付完成（Mock 用 true，其它通道 false）
 */
public record CreateOrderResult(
        String prepayId,
        Map<String, Object> clientPayload,
        boolean immediatelyPaid
) {
}
