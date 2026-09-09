package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.common.client.AppFlavor;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;

/**
 * Controller → PaymentService 的下单参数。
 *
 * <p>商品详情由调用方（业务模块）查好后通过 {@link ProductSnapshot} 传入，让 payment 模块对业务零认知，
 * 从而可跨 App 复用（去业务化，波 0）。
 *
 * @param userId         下单用户
 * @param product        商品快照（调用方从各自业务服务查到商品后转换传入）
 * @param channel        客户端指定通道（可空，为空时由 Router 按 flavor+平台决策）
 * @param clientPlatform 客户端平台（"android" / "ios"，从 ClientContext 取）
 * @param appFlavor      客户端 App Flavor（CN / GLOBAL / null），从 {@link com.lanprojects.fitcoach.common.client.ClientContext#appFlavor()} 取；
 *                       {@code null} 表示非 RN 客户端（admin/Postman/老版本），此时白名单校验会跳过
 * @param clientIp       客户端 IP（微信支付必填）
 */
public record CreateOrderCommand(
        Long userId,
        ProductSnapshot product,
        PaymentChannel channel,
        String clientPlatform,
        AppFlavor appFlavor,
        String clientIp
) {
}
