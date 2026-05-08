package com.lanprojects.fitcoach.payment.provider;

import com.lanprojects.fitcoach.payment.entity.PaymentChannel;

/**
 * 支付通道抽象 — 隔离"支付"业务和具体通道实现（微信 / Apple IAP / Mock）。
 *
 * <p><b>设计意图</b>：
 * <ul>
 *   <li>新增通道（如支付宝、Google Play）只需新增一个实现类，{@code PaymentService} / Controller 不变；</li>
 *   <li>{@link #channel()} 自描述自己是什么通道，PaymentChannelRouter 据此自动注册路由表；</li>
 *   <li>{@link #isAvailable()} 由实现类自行判断（如检查配置项 / 商户号是否已配齐），
 *       PaymentService 在路由前会调一次，不可用则抛 8101/8102；</li>
 *   <li>具体的回调验签 / 解析逻辑由各 Provider 在自己的 Controller 实现（不放接口里：
 *       因为各通道回调协议差别太大，强行抽象反而难看）。</li>
 * </ul>
 */
public interface PaymentChannelProvider {

    /** 自我标识 */
    PaymentChannel channel();

    /**
     * 是否可用（配置齐全 + 配置开关启用）。不可用时 PaymentService 会拒绝路由到此 Provider。
     * <p>各实现：
     * <ul>
     *   <li>微信：检查商户号 / API Key / 证书是否配齐 + payment.wechat.enabled=true；</li>
     *   <li>Apple：检查 issuer/keyId/共享密钥 + payment.apple.enabled=true（未接入返回 false）；</li>
     *   <li>Mock：检查 payment.mock.enabled=true（生产严禁开启）。</li>
     * </ul>
     */
    boolean isAvailable();

    /**
     * 创建通道侧订单，返回客户端拉起支付所需的 payload。
     * <p>实现方应：
     * <ul>
     *   <li>把传入的 {@link CreateOrderRequest#orderId()} 作为通道侧 out_trade_no（保证回调能反查到我方订单）；</li>
     *   <li>不在此方法落库，PaymentOrder 的入库由 PaymentService 在调用前完成；</li>
     *   <li>对外异常统一包装为 {@code BusinessException(PAYMENT_PROVIDER_ERROR)}，附详细 cause。</li>
     * </ul>
     */
    CreateOrderResult createOrder(CreateOrderRequest request);
}
