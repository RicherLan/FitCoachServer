package com.lanprojects.fitcoach.payment.provider.apple;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
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
 * Apple In-App Purchase Provider — 当前为<b>占位实现</b>，等开发者账号申请到位后接入。
 *
 * <p><b>关键差异</b>：Apple IAP 是<b>客户端发起 + 服务器验证收据</b>模式，与微信"服务器统一下单 → 客户端拉起"
 * 不同。所以 createOrder 在 IAP 场景下其实只是"在我方 DB 占位一条 PENDING 订单"，
 * 真正的支付流程：
 * <ol>
 *   <li>客户端用 StoreKit 直接向 Apple 发起 in-app purchase；</li>
 *   <li>客户端拿到 transactionReceipt 后调我方 {@code POST /api/payment/apple/verify}；</li>
 *   <li>服务器调 Apple verifyReceipt 接口（sandbox 或 production），验证通过后切订单为 PAID 并发布事件。</li>
 * </ol>
 *
 * <p><b>当前行为</b>：未接入前 isAvailable=false（永远不会被路由到），createOrder 抛 PROVIDER_ERROR 兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleIAPProvider implements PaymentChannelProvider {

    private final SysConfigService sysConfigService;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.APPLE_IAP;
    }

    @Override
    public boolean isAvailable() {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.APPLE_ENABLED, false)) {
            return false;
        }
        return notBlank(sysConfigService.getValue(PaymentConfigKeys.APPLE_BUNDLE_ID))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.APPLE_ROOT_CERTIFICATES))
                && (sysConfigService.getBoolValue(PaymentConfigKeys.APPLE_SANDBOX, false)
                    || notBlank(sysConfigService.getValue(PaymentConfigKeys.APPLE_APP_ID)));
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        String productId = sysConfigService.getValue(PaymentConfigKeys.APPLE_PRODUCT_PREFIX + request.productCode());
        if (!notBlank(productId)) { throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING, "Apple 商品映射未配置"); }
        // IAP 是「客户端发起 + 服务端验单」模式（与微信「服务端统一下单」不同）：
        //   1. createOrder 仅占位 PENDING 订单（PaymentService 已落库），返回 productCode 供客户端映射 appleProductId；
        //   2. 客户端用 StoreKit / react-native-iap 拉起购买，拿到 signedTransaction(JWS)；
        //   3. 客户端调 POST /api/payment/apple/verify 提交 orderId + signedTransaction；
        //   4. 服务端验签解析 → markPaid → 发 PaymentSucceededEvent。
        log.info("[apple-iap] 创建 IAP 占位订单 orderId={} userId={} productCode={}",
                request.orderId(), request.userId(), request.productCode());
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "APPLE_IAP");
        payload.put("orderId", request.orderId());
        payload.put("productCode", request.productCode());
        payload.put("productId", productId);
        payload.put("appAccountToken", AppleSignedDataVerifier.accountToken(request.orderId()).toString());
        payload.put("amountCents", request.amountCents());
        payload.put("currency", request.currency());
        payload.put("message", "请在客户端通过 App Store 完成购买后调用 /api/payment/apple/verify 验单");
        return new CreateOrderResult(null, payload, false);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
