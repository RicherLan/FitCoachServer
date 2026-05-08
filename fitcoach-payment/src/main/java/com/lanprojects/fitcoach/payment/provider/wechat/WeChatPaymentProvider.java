package com.lanprojects.fitcoach.payment.provider.wechat;

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
 * 微信支付 Provider — 当前为<b>骨架占位</b>，待开发者申请到微信商户号后实现 V3 协议：
 * <ol>
 *   <li>统一下单 (Native/JSAPI/App)：POST /v3/pay/transactions/app；</li>
 *   <li>签名构造（私钥 RSA-SHA256）；</li>
 *   <li>HTTP 调用 + 解析 prepay_id；</li>
 *   <li>构造客户端拉起所需的二次签名 payload。</li>
 * </ol>
 *
 * <p><b>当前行为</b>：{@link #isAvailable()} 检查配置是否齐全 + 总开关；缺则返回 false。
 * {@link #createOrder} 在 isAvailable=true 但实现未补完时，抛 PAYMENT_PROVIDER_ERROR 防止"误以为接通了"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatPaymentProvider implements PaymentChannelProvider {

    private final SysConfigService sysConfigService;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.WECHAT;
    }

    @Override
    public boolean isAvailable() {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.WECHAT_ENABLED, false)) {
            return false;
        }
        // 必填配置项
        return notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_APP_ID))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_MCH_ID))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_API_V3_KEY))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_NOTIFY_URL));
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        // TODO(P2): 接入微信支付 V3 后实现：
        //  1. 用 SysConfig 取 appId / mchId / apiV3Key / mchSerialNo / notifyUrl
        //  2. 调用 https://api.mch.weixin.qq.com/v3/pay/transactions/app 完成统一下单
        //  3. 拿到 prepay_id 后构造客户端二次签名 payload (timeStamp/nonceStr/package/signType/paySign)
        //  4. 把 payload 放入 CreateOrderResult.clientPayload，immediatelyPaid=false
        //  5. 异常统一包 BusinessException(PAYMENT_PROVIDER_ERROR)
        log.error("[wechat-pay] 微信支付未接入：orderId={} userId={} amountCents={} —— 请联系管理员配置商户号",
                request.orderId(), request.userId(), request.amountCents());
        throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR,
                "微信支付通道暂未接入，请稍后再试或选择其他支付方式");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
