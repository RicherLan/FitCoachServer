package com.lanprojects.fitcoach.payment.provider.wechat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付 V3 回调处理器 — 校验签名 → 解密 → 提取关键字段 → 调 PaymentService.markPaid。
 *
 * <p><b>回调 body 结构</b>（通知外层）：
 * <pre>
 * {
 *   "id": "通知ID",
 *   "create_time": "2021-01-01T00:00:00+08:00",
 *   "event_type": "TRANSACTION.SUCCESS",
 *   "resource_type": "encrypt-resource",
 *   "resource": {
 *     "algorithm": "AEAD_AES_256_GCM",
 *     "ciphertext": "...",
 *     "associated_data": "transaction",
 *     "nonce": "...",
 *     "original_type": "transaction"
 *   },
 *   "summary": "支付成功"
 * }
 * </pre>
 *
 * <p><b>解密后的 resource 数据</b>（支付结果）：
 * <pre>
 * {
 *   "appid": "wx...",
 *   "mchid": "...",
 *   "out_trade_no": "202506...",
 *   "transaction_id": "4200...",
 *   "trade_state": "SUCCESS",
 *   "amount": { "total": 100, "payer_total": 100, "currency": "CNY" },
 *   ...
 * }
 * </pre>
 *
 * @see <a href="https://pay.weixin.qq.com/docs/merchant/apis/in-app-payment/payment-notice.html">
 *     微信支付结果通知</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatCallbackHandler {

    private final SysConfigService sysConfigService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * 处理微信支付回调。
     *
     * @param wechatpayTimestamp  请求头 Wechatpay-Timestamp
     * @param wechatpayNonce     请求头 Wechatpay-Nonce
     * @param wechatpaySignature 请求头 Wechatpay-Signature
     * @param wechatpaySerial    请求头 Wechatpay-Serial（微信平台证书序列号）
     * @param body               原始请求体
     * @return true 表示处理成功，微信应收到 SUCCESS 响应
     */
    public boolean handleCallback(String wechatpayTimestamp, String wechatpayNonce,
                                   String wechatpaySignature, String wechatpaySerial,
                                   String body) {
        log.info("[wechat-pay] 收到微信回调 bodyLen={}", body == null ? 0 : body.length());

        // 1. 签名校验（MVP 阶段跳过，见 WeChatPayV3Helper.verifyCallbackSignature 注释）
        if (!WeChatPayV3Helper.verifyCallbackSignature(
                wechatpayTimestamp, wechatpayNonce, body, wechatpaySignature)) {
            log.error("[wechat-pay] 回调签名校验失败");
            return false;
        }

        try {
            // 2. 解析外层 JSON
            Map<String, Object> outerMap = objectMapper.readValue(body, new TypeReference<>() {});
            String eventType = (String) outerMap.get("event_type");
            log.info("[wechat-pay] 回调事件类型 event_type={}", eventType);

            // 只处理支付成功事件
            if (!"TRANSACTION.SUCCESS".equals(eventType)) {
                log.info("[wechat-pay] 非支付成功事件，忽略 event_type={}", eventType);
                return true; // 返回 SUCCESS，告知微信不要重试
            }

            // 3. 解密 resource
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) outerMap.get("resource");
            if (resource == null) {
                log.error("[wechat-pay] 回调 body 缺少 resource 字段");
                return false;
            }

            String nonce = (String) resource.get("nonce");
            String associatedData = (String) resource.get("associated_data");
            String ciphertext = (String) resource.get("ciphertext");

            String apiV3Key = sysConfigService.getValue(PaymentConfigKeys.WECHAT_API_V3_KEY);
            if (apiV3Key == null || apiV3Key.isBlank()) {
                log.error("[wechat-pay] 系统配置缺少 API V3 密钥，无法解密回调");
                return false;
            }

            String decryptedJson = WeChatPayV3Helper.decryptAesGcm(apiV3Key, nonce, associatedData, ciphertext);
            log.info("[wechat-pay] 回调解密成功 decryptedLen={}", decryptedJson.length());

            // 4. 解析解密后的交易数据
            Map<String, Object> transaction = objectMapper.readValue(decryptedJson, new TypeReference<>() {});
            String outTradeNo = (String) transaction.get("out_trade_no");
            String transactionId = (String) transaction.get("transaction_id");
            String tradeState = (String) transaction.get("trade_state");

            log.info("[wechat-pay] 回调交易信息 out_trade_no={} transaction_id={} trade_state={}",
                    outTradeNo, transactionId, tradeState);

            if (!"SUCCESS".equals(tradeState)) {
                log.warn("[wechat-pay] 交易状态非 SUCCESS，忽略 tradeState={} orderId={}",
                        tradeState, outTradeNo);
                return true; // 微信仍收到 SUCCESS ack
            }

            if (outTradeNo == null || outTradeNo.isBlank()) {
                log.error("[wechat-pay] 解密后 out_trade_no 为空");
                return false;
            }

            // 5. 标记订单已支付（幂等 — markPaid 内部处理重复调用）
            paymentService.markPaid(outTradeNo, null, transactionId);
            log.info("[wechat-pay] 订单标记支付成功 orderId={} transactionId={}", outTradeNo, transactionId);
            return true;

        } catch (BusinessException be) {
            log.error("[wechat-pay] 回调处理业务异常 msg={}", be.getMessage());
            // 业务异常（如订单不存在）也返回 SUCCESS，防止微信无限重试
            return true;
        } catch (Exception e) {
            log.error("[wechat-pay] 回调处理异常", e);
            return false; // 返回 FAIL，微信会重试
        }
    }
}
