package com.lanprojects.fitcoach.payment.provider.mock;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.provider.CreateOrderRequest;
import com.lanprojects.fitcoach.payment.provider.CreateOrderResult;
import com.lanprojects.fitcoach.payment.provider.PaymentChannelProvider;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock 支付通道 — 开发期使用：下单立即返回"支付成功"，PaymentService 据此立即触发
 * 会员激活流程，方便端到端联调而不必申请微信商户号 / 苹果开发者账号。
 *
 * <p><b>启用方式</b>：在 admin 后台或 sys_config 表设置 {@code payment.mock.enabled=true}。
 *
 * <p><b>⚠ 生产环境严禁启用</b>。CI/上线前的检查项里会校验该值（待补）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentChannelProvider {

    private final SysConfigService sysConfigService;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.MOCK;
    }

    @Override
    public boolean isAvailable() {
        return sysConfigService.getBoolValue(PaymentConfigKeys.MOCK_ENABLED, false);
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        log.warn("[mock-pay] 开发期 Mock 支付：orderId={} userId={} planCode={} amountCents={}",
                request.orderId(), request.userId(), request.planCode(), request.amountCents());

        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "MOCK");
        payload.put("orderId", request.orderId());
        payload.put("amountCents", request.amountCents());
        payload.put("currency", request.currency());
        payload.put("message", "Mock 支付：本订单将立即视为已支付（仅开发环境）");

        // immediatelyPaid=true：PaymentService 收到后会同事务里把订单切到 PAID 并发布 PaymentSucceededEvent
        return new CreateOrderResult(
                "MOCK_PREPAY_" + request.orderId(),
                payload,
                true
        );
    }
}
