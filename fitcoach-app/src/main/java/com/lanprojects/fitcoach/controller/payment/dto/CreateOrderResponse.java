package com.lanprojects.fitcoach.controller.payment.dto;

import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * POST /api/payment/order 的响应。
 *
 * <p>客户端拿到本响应后的处理流程：
 * <ol>
 *   <li>检查 {@link #immediatelyPaid}：true 时（MOCK 通道）直接刷新会员状态，跳支付成功页；</li>
 *   <li>否则按 {@link #channel} 调对应 SDK，参数从 {@link #clientPayload} 取；</li>
 *   <li>支付完成后再次调 {@code /api/membership/my-status} 拿最新状态（不要相信本地状态）。</li>
 * </ol>
 *
 * @param orderId         业务订单号（客户端落本地，便于失败重试 / 定位订单）
 * @param channel         实际下单走的通道
 * @param amountCents     实付金额（最小货币单位，分/美分）
 * @param currency        币种 ISO 代码：CNY / USD
 * @param clientPayload   客户端拉起 SDK 所需的参数 map（不同通道字段不同）
 * @param immediatelyPaid 通道下单即视为支付成功（MOCK 通道用，客户端无需调 SDK）
 */
@Builder
public record CreateOrderResponse(
        String orderId,
        PaymentChannel channel,
        int amountCents,
        String currency,
        Map<String, Object> clientPayload,
        boolean immediatelyPaid
) {
}
