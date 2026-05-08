package com.lanprojects.fitcoach.payment.service;

/**
 * 套餐快照 — Controller / 上层服务调 {@link PaymentService#createOrder} 时传入的最小 plan 信息。
 *
 * <p><b>设计意图</b>：让 fitcoach-payment 模块不依赖 fitcoach-membership 模块。
 * <ul>
 *   <li>"取 plan 详情"由调用方负责（Controller 在 fitcoach-app 层、同时持有 membership 和 payment 两模块）；</li>
 *   <li>PaymentService 只关心订单本身，不关心 plan 在哪儿存、长啥样；</li>
 *   <li>金额单位仍是最小货币单位（分/美分）；</li>
 *   <li>{@code priceUsdCents} 在 IAP 接入前可为 null，但走 APPLE_IAP / GOOGLE_PLAY 通道时必须有值，
 *       否则 PaymentService 会抛 PAYMENT_CONFIG_MISSING。</li>
 * </ul>
 *
 * @param planCode       套餐 code
 * @param displayName    展示名（用于落 PaymentOrder.planSnapshotName 防止套餐改名后历史订单错乱）
 * @param priceCny       国内价格（分），不能为 null
 * @param priceUsdCents  海外价格（美分），可空（IAP 接入前）
 */
public record PlanSnapshot(
        String planCode,
        String displayName,
        Integer priceCny,
        Integer priceUsdCents
) {
}
