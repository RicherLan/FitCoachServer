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
 * <p>去业务化（波 0）：商品用通用的 {@code productType + productCode + productName} 描述，
 * Provider 不认识"会员"等业务概念。
 *
 * @param orderId          业务订单号（已由 PaymentService 生成，Provider 必须用这个作为通道侧的 out_trade_no）
 * @param userId           购买用户 id
 * @param productType      商品类型（业务方自定义，如 "MEMBERSHIP"）
 * @param productCode      商品业务 code（如会员套餐 "MONTHLY"）
 * @param productName      商品显示名称（用于通道侧 body / description 字段）
 * @param amountCents      实付金额（最小货币单位）
 * @param currency         币种（CNY / USD）
 * @param clientPlatform   客户端平台 ("android" / "ios"，可能 null)
 * @param clientIp         客户端 IP（微信 H5/Native 必填字段）
 * @param attachJson       透传字段（落微信 attach），目前为 userId + productType + productCode 的简易 JSON
 */
public record CreateOrderRequest(
        String orderId,
        Long userId,
        String productType,
        String productCode,
        String productName,
        int amountCents,
        String currency,
        String clientPlatform,
        String clientIp,
        String attachJson
) {
}
