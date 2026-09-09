package com.lanprojects.fitcoach.payment.provider.google;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.provider.CreateOrderRequest;
import com.lanprojects.fitcoach.payment.provider.CreateOrderResult;
import com.lanprojects.fitcoach.payment.provider.PaymentChannelProvider;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.payment.provider.RefundRequest;
import com.lanprojects.fitcoach.payment.provider.RefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Google Play Billing Provider（波 4 骨架）—— 海外 Android 数字商品通道。
 *
 * <p><b>支付模型</b>：与 Apple IAP 相同的「客户端发起 + 服务端验单」：
 * <ol>
 *   <li>createOrder 占位 PENDING 订单，返回 productCode 供客户端 Google Play Billing 拉起购买；</li>
 *   <li>客户端购买成功拿到 purchaseToken，调 {@code POST /api/payment/google/verify}；</li>
 *   <li>服务端调 Google Play Developer API 验证 purchaseToken → markPaid（见 GooglePlayService）。</li>
 * </ol>
 *
 * <p><b>退款</b>：Google Play 支持商户主动退款（{@link #supportsActiveRefund()}=true），
 * 调 Google Play Developer API 的 orders.refund；同时也可能收到 RTDN 的 VOIDED_PURCHASE 通知（被动）。
 *
 * <p><b>⚠️ 生产必须补全（当前骨架，等 Google Play Console + Service Account）</b>：
 * verify / refund 的真实 Google Play Developer API 调用需要用 Service Account 凭证换取 OAuth token +
 * AndroidPublisher client。当前为结构骨架。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePlayProvider implements PaymentChannelProvider {

    private final SysConfigService sysConfigService;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.GOOGLE_PLAY;
    }

    @Override
    public boolean isAvailable() {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.GOOGLE_PLAY_ENABLED, false)) {
            return false;
        }
        return notBlank(sysConfigService.getValue(PaymentConfigKeys.GOOGLE_PLAY_PACKAGE_NAME))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON));
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        // 客户端发起模式（同 Apple IAP）：占位 PENDING 订单（PaymentService 已落库），
        // 返回 productCode 供客户端映射 Google Play productId 并用 Billing 拉起购买。
        log.info("[google-play] 创建占位订单 orderId={} userId={} productCode={}",
                request.orderId(), request.userId(), request.productCode());
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "GOOGLE_PLAY");
        payload.put("orderId", request.orderId());
        payload.put("productCode", request.productCode());
        payload.put("amountCents", request.amountCents());
        payload.put("currency", request.currency());
        payload.put("message", "请在客户端通过 Google Play 完成购买后调用 /api/payment/google/verify 验单");
        return new CreateOrderResult(null, payload, false);
    }

    @Override
    public boolean supportsActiveRefund() {
        return true;
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        // TODO(生产必做)：用 Service Account 凭证换 OAuth token，调 Google Play Developer API
        //   androidpublisher.orders.refund（按 Google order id）把款项原路退回。
        //   当前骨架未接真实 API —— RefundService 会捕获此异常并把退款单置 FAILED，待接入后重试。
        log.warn("[google-play] 退款骨架未接真实 API refundNo={} orderId={}",
                request.refundNo(), request.orderId());
        throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR,
                "Google Play 退款接口待接入（需 Service Account + AndroidPublisher client）");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
