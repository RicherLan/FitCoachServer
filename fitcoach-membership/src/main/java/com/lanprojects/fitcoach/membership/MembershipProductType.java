package com.lanprojects.fitcoach.membership;

/**
 * 会员业务的商品类型标识 —— 对应 payment 模块通用契约（{@code ProductSnapshot.productType} /
 * {@code PaymentSucceededEvent.productType}）里的 productType 值。
 *
 * <p><b>去业务化架构（波 0）的关键落点</b>：payment 模块被设计成可跨 App 复用，它<strong>不认识</strong>
 * "会员"这个概念，只把 productType 当作一个透传标签。"MEMBERSHIP" 这个具体值由会员业务模块（本模块）
 * 自己约定：
 * <ul>
 *   <li>下单时（{@code PaymentController}）：以 {@code productType = MEMBERSHIP} 构造 {@code ProductSnapshot}；</li>
 *   <li>回调时（{@code MembershipService#onPaymentSucceeded}）：只认领 {@code productType == MEMBERSHIP}
 *       的支付成功事件，其它类型（如未来其它 App 的 "COINS" / "COURSE"）交给对应业务模块监听。</li>
 * </ul>
 *
 * <p>换个 App 卖别的商品时，payment 模块一行不改，新业务只需定义自己的 productType 常量 + 监听器。
 */
public final class MembershipProductType {

    private MembershipProductType() {}

    /** 会员商品类型标识。下单传给 payment 的 productType，支付成功事件按此认领。 */
    public static final String MEMBERSHIP = "MEMBERSHIP";
}
