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
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.APPLE_SHARED_SECRET));
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        // TODO(P4): 苹果开发者账号申请后实现：
        //  1. 此方法返回的 clientPayload 主要供客户端确认 plan/amount 显示用，IAP 不需要 prepay_id
        //  2. 客户端拿 result 后用 RN react-native-iap 的 requestSubscription / requestPurchase 自己拉起
        //  3. 客户端拿到 transactionReceipt 调 /api/payment/apple/verify 完成验单 → 切 PAID
        log.error("[apple-iap] Apple IAP 未接入：orderId={} userId={} planCode={} —— 等待苹果开发者账号申请",
                request.orderId(), request.userId(), request.planCode());
        throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR,
                "Apple In-App Purchase 即将上线，请耐心等待");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
