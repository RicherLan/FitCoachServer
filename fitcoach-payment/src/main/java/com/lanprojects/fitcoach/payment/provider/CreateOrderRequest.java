package com.lanprojects.fitcoach.payment.provider;

/**
 * Provider 创建订单的请求参数（PaymentService → Provider 的契约）。
 *
 * <p>用 record 而非 class：
 * <ul>
 *   <li>不可变 + 自动 equals/hashCode/toString，适合做 DTO；</li>
 *   <li>字段一目了然，便于多实现方对齐。</li>
 * </ul>
 *
 * @param orderId          业务订单号（已由 PaymentService 生成，Provider 必须用这个作为通道侧的 out_trade_no）
 * @param userId           购买用户 id
 * @param planCode         套餐 code（DAILY / MONTHLY / ...）
 * @param planDisplayName  套餐显示名称（用于 body / description 字段）
 * @param amountCents      实付金额（最小货币单位）
 * @param currency         币种（CNY / USD）
 * @param clientPlatform   客户端平台 ("android" / "ios"，可能 null)
 * @param clientIp         客户端 IP（微信 H5/Native 必填字段）
 * @param attachJson       透传字段（落微信 attach），目前为 planCode + userId 的简易 JSON
 */
public record CreateOrderRequest(
        String orderId,
        Long userId,
        String planCode,
        String planDisplayName,
        int amountCents,
        String currency,
        String clientPlatform,
        String clientIp,
        String attachJson
) {
}
