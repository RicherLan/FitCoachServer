package com.lanprojects.fitcoach.payment.service;

/**
 * 商品快照 — Controller / 上层业务服务调 {@link PaymentService#createOrder} 时传入的通用商品信息。
 *
 * <p><b>去业务化设计（波 0）</b>：这是让 fitcoach-payment 成为「可跨 App 复用的支付内核」的关键契约。
 * payment 模块<strong>完全不认识</strong>"会员/金币/课程"这些业务概念，只把商品抽象成
 * {@code (productType, productCode, productName, 价格)} 四元组：
 * <ul>
 *   <li>"取商品详情 / 定价"由调用方（业务模块，如 fitcoach-membership）负责；</li>
 *   <li>PaymentService 只关心订单本身，不关心商品在哪儿存、长啥样、是什么；</li>
 *   <li>金额单位是最小货币单位（分 / 美分）；</li>
 *   <li>{@code priceUsdCents} 在 IAP 接入前可为 null，但走 APPLE_IAP / GOOGLE_PLAY 通道时必须有值，
 *       否则 PaymentService 会抛 PAYMENT_CONFIG_MISSING。</li>
 * </ul>
 *
 * <p><b>为什么 productType 是 String 而非枚举</b>：枚举会把"有哪些商品类型"编译期固化进 payment 模块，
 * 换个 App（卖金币 / 课程）就得改 payment 源码。用 String 让 payment 模块对业务零认知——
 * 业务方自己约定值（会员用 {@code "MEMBERSHIP"}，金币用 {@code "COINS"}），payment 仅透传落库 +
 * 随 {@link com.lanprojects.fitcoach.common.event.PaymentSucceededEvent} 回抛，业务方监听后据此分发。
 *
 * @param productType   商品类型（业务方自定义，如 "MEMBERSHIP" / "COINS" / "COURSE"），不能为 null
 * @param productCode   商品业务 code（如会员套餐 planCode "MONTHLY"），不能为 null
 * @param productName   商品展示名快照（落 PaymentOrder.productName，防改名后历史订单错乱），不能为 null
 * @param priceCny      国内价格（分），不能为 null
 * @param priceUsdCents 海外价格（美分），可空（IAP 接入前）
 */
public record ProductSnapshot(
        String productType,
        String productCode,
        String productName,
        Integer priceCny,
        Integer priceUsdCents
) {
}
