package com.lanprojects.fitcoach.payment.provider.wechat;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 V3 — App 下单 Provider。
 *
 * <p><b>核心流程</b>：
 * <ol>
 *   <li>从 SysConfig 读取 appId / mchId / apiV3Key / mchSerialNo / notifyUrl / merchantPrivateKey；</li>
 *   <li>调用 {@code POST /v3/pay/transactions/app} 完成 App 统一下单；</li>
 *   <li>拿到 {@code prepay_id} 后构造客户端二次签名 payload（appId/partnerId/prepayId/nonceStr/timeStamp/sign）；</li>
 *   <li>返回 {@link CreateOrderResult}（immediatelyPaid=false），客户端拿 payload 调 WXApi.sendReq 拉起支付。</li>
 * </ol>
 *
 * <p><b>签名方案</b>：RSA-SHA256（商户私钥签名），不使用微信官方 SDK，仅用 JDK 原生密码学 + hutool-http。
 * <p><b>安全</b>：商户私钥通过 SysConfig 加密存储（AES-256-GCM），运行时由 SysConfigService 自动解密后缓存明文。
 *
 * @see <a href="https://pay.weixin.qq.com/docs/merchant/apis/in-app-payment/direct-jsons/app-prepay.html">
 *     微信支付 V3 App 下单 API</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatPaymentProvider implements PaymentChannelProvider {

    private static final String WECHAT_APP_ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/app";
    private static final String WECHAT_APP_ORDER_PATH = "/v3/pay/transactions/app";
    private static final String WECHAT_REFUND_URL = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";
    private static final String WECHAT_REFUND_PATH = "/v3/refund/domestic/refunds";

    /** HTTP 请求超时：连接 5s / 读取 10s */
    private static final int CONNECT_TIMEOUT = 5_000;
    private static final int READ_TIMEOUT = 10_000;

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;
    private final WeChatSignatureVerifier signatureVerifier;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.WECHAT;
    }

    @Override
    public boolean isAvailable() {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.WECHAT_ENABLED, false)) {
            return false;
        }
        // 必填配置项全部非空才算可用
        return notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_APP_ID))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_MCH_ID))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_API_V3_KEY))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_MCH_SERIAL_NO))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_NOTIFY_URL))
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY))
                && sysConfigService.getValue(PaymentConfigKeys.WECHAT_API_V3_KEY).getBytes(java.nio.charset.StandardCharsets.UTF_8).length == 32
                && httpsUrl(sysConfigService.getValue(PaymentConfigKeys.WECHAT_NOTIFY_URL))
                && signatureVerifier.isConfigured();
    }

    /**
     * 调用微信支付 V3 App 统一下单接口。
     *
     * <p>完整流程：
     * <ol>
     *   <li>构建 JSON body（appid, mchid, description, out_trade_no, notify_url, amount, scene_info）；</li>
     *   <li>用商户私钥 RSA-SHA256 构造 Authorization header；</li>
     *   <li>POST 调用微信 API，解析 prepay_id；</li>
     *   <li>二次签名构造客户端 payload。</li>
     * </ol>
     */
    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        // 1. 读取所有配置
        String appId = requireConfig(PaymentConfigKeys.WECHAT_APP_ID, "微信 AppID");
        String mchId = requireConfig(PaymentConfigKeys.WECHAT_MCH_ID, "微信商户号");
        String apiV3Key = requireConfig(PaymentConfigKeys.WECHAT_API_V3_KEY, "API V3 密钥");
        String serialNo = requireConfig(PaymentConfigKeys.WECHAT_MCH_SERIAL_NO, "商户证书序列号");
        String notifyUrl = requireConfig(PaymentConfigKeys.WECHAT_NOTIFY_URL, "回调 URL");
        String privateKeyPem = requireConfig(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY, "商户私钥");

        PrivateKey privateKey;
        try {
            privateKey = WeChatPayV3Helper.loadPrivateKeyFromPem(privateKeyPem);
        } catch (Exception e) {
            log.error("[wechat-pay] 商户私钥解析失败 orderId={}", request.orderId(), e);
            throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR, "微信支付配置错误（私钥无效）");
        }

        // 2. 构建请求体 JSON
        String bodyJson = buildOrderBodyJson(request, appId, mchId, notifyUrl);
        log.info("[wechat-pay] 统一下单 orderId={} amountCents={} currency={}",
                request.orderId(), request.amountCents(), request.currency());

        // 3. 构造 Authorization header
        String authorization = WeChatPayV3Helper.buildAuthorizationHeader(
                "POST", WECHAT_APP_ORDER_PATH, bodyJson, mchId, serialNo, privateKey);

        // 4. 发起 HTTP 请求
        String prepayId;
        try (HttpResponse response = HttpRequest.post(WECHAT_APP_ORDER_URL)
                .header("Authorization", authorization)
                .header("Wechatpay-Serial", signatureVerifier.preferredSerial())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(bodyJson)
                .timeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .execute()) {

            int status = response.getStatus();
            String respBody = response.body();
            if (status >= 200 && status < 300 && !signatureVerifier.verify(
                    response.header("Wechatpay-Timestamp"), response.header("Wechatpay-Nonce"),
                    respBody, response.header("Wechatpay-Signature"), response.header("Wechatpay-Serial"))) {
                throw new IllegalStateException("微信支付API应答验签失败，拒绝使用响应");
            }
            log.info("[wechat-pay] 统一下单响应 orderId={} status={} bodyLen={}",
                    request.orderId(), status, respBody == null ? 0 : respBody.length());

            if (status < 200 || status >= 300) {
                log.error("[wechat-pay] 统一下单失败 orderId={} status={} body={}",
                        request.orderId(), status, respBody);
                // 尝试解析微信的错误 message
                String errMsg = parseWechatErrorMessage(respBody);
                throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR,
                        "微信支付下单失败：" + errMsg);
            }

            // 5. 解析 prepay_id
            Map<String, Object> respMap = objectMapper.readValue(respBody, new TypeReference<>() {});
            prepayId = (String) respMap.get("prepay_id");
            if (prepayId == null || prepayId.isBlank()) {
                log.error("[wechat-pay] 返回 prepay_id 为空 orderId={} resp={}", request.orderId(), respBody);
                throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR, "微信支付返回 prepay_id 为空");
            }

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[wechat-pay] 统一下单异常 orderId={}", request.orderId(), e);
            throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR,
                    "微信支付下单异常：" + e.getMessage());
        }

        // 6. 二次签名 — 构造客户端 App 拉起支付的 payload
        Map<String, Object> clientPayload = WeChatPayV3Helper.buildAppPayload(
                appId, mchId, prepayId, privateKey);
        log.info("[wechat-pay] 二次签名完成 orderId={} prepayId={}", request.orderId(), prepayId);

        return new CreateOrderResult(prepayId, clientPayload, false);
    }

    /** 查单补偿只使用验签后的微信交易数据。 */
    public Map<String, Object> queryTransaction(String orderId) {
        String mchId = requireConfig(PaymentConfigKeys.WECHAT_MCH_ID, "微信商户号");
        String path = "/v3/pay/transactions/out-trade-no/" + java.net.URLEncoder.encode(orderId, java.nio.charset.StandardCharsets.UTF_8)
                + "?mchid=" + java.net.URLEncoder.encode(mchId, java.nio.charset.StandardCharsets.UTF_8);
        var key = WeChatPayV3Helper.loadPrivateKeyFromPem(requireConfig(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY, "商户私钥"));
        String authorization = WeChatPayV3Helper.buildAuthorizationHeader("GET", path, "", mchId,
                requireConfig(PaymentConfigKeys.WECHAT_MCH_SERIAL_NO, "商户证书序列号"), key);
        try (HttpResponse response = HttpRequest.get("https://api.mch.weixin.qq.com" + path)
                .header("Authorization", authorization).header("Wechatpay-Serial", signatureVerifier.preferredSerial())
                .header("Accept", "application/json").timeout(CONNECT_TIMEOUT).setReadTimeout(READ_TIMEOUT).execute()) {
            String body = response.body();
            if (response.getStatus() != 200 || !signatureVerifier.verify(response.header("Wechatpay-Timestamp"),
                    response.header("Wechatpay-Nonce"), body, response.header("Wechatpay-Signature"), response.header("Wechatpay-Serial"))) {
                throw new IllegalStateException("微信查单失败或应答验签失败");
            }
            Map<String, Object> transaction = objectMapper.readValue(body, new TypeReference<>() {});
            if (!orderId.equals(transaction.get("out_trade_no")) || !mchId.equals(transaction.get("mchid"))
                    || !requireConfig(PaymentConfigKeys.WECHAT_APP_ID, "微信AppID").equals(transaction.get("appid"))) {
                throw new IllegalStateException("微信查单的订单/商户/应用不匹配");
            }
            return transaction;
        } catch (Exception e) { throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR, "微信查单未确认，请稍后重试"); }
    }

    /** 必须微信关单确认成功后才可关闭本地订单，避免本地取消后仍被扣款。 */
    public void closeRemoteOrder(String orderId) {
        String mchId = requireConfig(PaymentConfigKeys.WECHAT_MCH_ID, "微信商户号");
        String path = "/v3/pay/transactions/out-trade-no/" + java.net.URLEncoder.encode(orderId, java.nio.charset.StandardCharsets.UTF_8) + "/close";
        try {
            String body = objectMapper.writeValueAsString(Map.of("mchid", mchId));
            var key = WeChatPayV3Helper.loadPrivateKeyFromPem(requireConfig(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY, "商户私钥"));
            String authorization = WeChatPayV3Helper.buildAuthorizationHeader("POST", path, body, mchId,
                    requireConfig(PaymentConfigKeys.WECHAT_MCH_SERIAL_NO, "商户证书序列号"), key);
            try (HttpResponse response = HttpRequest.post("https://api.mch.weixin.qq.com" + path)
                    .header("Authorization", authorization).header("Wechatpay-Serial", signatureVerifier.preferredSerial())
                    .header("Content-Type", "application/json").body(body).timeout(CONNECT_TIMEOUT).setReadTimeout(READ_TIMEOUT).execute()) {
                if (response.getStatus() != 204 || !signatureVerifier.verify(
                        response.header("Wechatpay-Timestamp"), response.header("Wechatpay-Nonce"),
                        "", response.header("Wechatpay-Signature"), response.header("Wechatpay-Serial"))) {
                    throw new IllegalStateException("微信关单未确认或应答验签失败");
                }
            }
        } catch (Exception e) { throw new BusinessException(ResultCode.PAYMENT_PROVIDER_ERROR, "微信关单未确认，保留订单等待核实"); }
    }

    // ====== 退款（波 2）======

    @Override
    public boolean supportsActiveRefund() {
        return true;
    }

    /**
     * 微信退款（V3）—— 调用 {@code POST /v3/refund/domestic/refunds} 把钱原路退回。
     *
     * <p>响应 {@code status} 语义：SUCCESS 退款成功 / PROCESSING 退款处理中（等退款回调）/
     * CLOSED 退款关闭 / ABNORMAL 退款异常。SUCCESS 时 {@code synchronouslyCompleted=true} 可直接置完成；
     * PROCESSING 时受理成功但需等退款结果回调（{@code refundNotifyUrl}）再置完成。
     *
     * @see <a href="https://pay.weixin.qq.com/docs/merchant/apis/refund/refunds/create.html">微信退款 API</a>
     */
    @Override
    public RefundResult refund(RefundRequest request) {
        String mchId = requireConfig(PaymentConfigKeys.WECHAT_MCH_ID, "微信商户号");
        String serialNo = requireConfig(PaymentConfigKeys.WECHAT_MCH_SERIAL_NO, "商户证书序列号");
        String privateKeyPem = requireConfig(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY, "商户私钥");
        String refundNotifyUrl = requireConfig(PaymentConfigKeys.WECHAT_REFUND_NOTIFY_URL, "微信退款回调URL");
        if (!httpsUrl(refundNotifyUrl)) throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "微信退款回调必须为HTTPS地址");

        PrivateKey privateKey;
        try {
            privateKey = WeChatPayV3Helper.loadPrivateKeyFromPem(privateKeyPem);
        } catch (Exception e) {
            log.error("[wechat-refund] 商户私钥解析失败 refundNo={}", request.refundNo(), e);
            throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "微信退款配置错误（私钥无效）");
        }

        String bodyJson = buildRefundBodyJson(request, refundNotifyUrl);
        String authorization = WeChatPayV3Helper.buildAuthorizationHeader(
                "POST", WECHAT_REFUND_PATH, bodyJson, mchId, serialNo, privateKey);
        log.info("[wechat-refund] 发起退款 refundNo={} orderId={} refundCents={}",
                request.refundNo(), request.orderId(), request.refundCents());

        try (HttpResponse response = HttpRequest.post(WECHAT_REFUND_URL)
                .header("Authorization", authorization)
                .header("Wechatpay-Serial", signatureVerifier.preferredSerial())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(bodyJson)
                .timeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .execute()) {

            int status = response.getStatus();
            String respBody = response.body();
            if (status >= 200 && status < 300 && !signatureVerifier.verify(
                    response.header("Wechatpay-Timestamp"), response.header("Wechatpay-Nonce"),
                    respBody, response.header("Wechatpay-Signature"), response.header("Wechatpay-Serial"))) {
                throw new IllegalStateException("微信支付API应答验签失败，拒绝使用响应");
            }
            if (status < 200 || status >= 300) {
                log.error("[wechat-refund] 退款失败 refundNo={} status={} body={}",
                        request.refundNo(), status, respBody);
                if (status >= 400 && status < 500 && status != 409 && status != 429) {
                    return new RefundResult(null, false, false, respBody);
                }
                throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR,
                        "微信退款失败：" + parseWechatErrorMessage(respBody));
            }

            Map<String, Object> respMap = objectMapper.readValue(respBody, new TypeReference<>() {});
            String refundId = (String) respMap.get("refund_id");
            String refundState = (String) respMap.get("status");
            boolean syncCompleted = "SUCCESS".equals(refundState);
            boolean accepted = syncCompleted || "PROCESSING".equals(refundState);
            if (!accepted) {
                log.error("[wechat-refund] 退款状态异常 refundNo={} state={} body={}",
                        request.refundNo(), refundState, respBody);
                throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR,
                        "微信退款状态异常：" + refundState);
            }
            log.info("[wechat-refund] 退款受理 refundNo={} refundId={} state={} sync={}",
                    request.refundNo(), refundId, refundState, syncCompleted);
            return new RefundResult(refundId, true, syncCompleted, respBody);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[wechat-refund] 退款异常 refundNo={}", request.refundNo(), e);
            throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "微信退款异常：" + e.getMessage());
        }
    }

    /** 构建微信退款请求 JSON（out_trade_no + out_refund_no + amount{refund,total,currency}）。 */
    private String buildRefundBodyJson(RefundRequest request, String refundNotifyUrl) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("out_trade_no", request.orderId());
            body.put("out_refund_no", request.refundNo());
            if (request.reason() != null && !request.reason().isBlank()) {
                body.put("reason", truncate(request.reason(), 80));
            }
            if (refundNotifyUrl != null && !refundNotifyUrl.isBlank()) {
                body.put("notify_url", refundNotifyUrl);
            }
            Map<String, Object> amount = new LinkedHashMap<>();
            amount.put("refund", request.refundCents());
            amount.put("total", request.totalAmountCents());
            amount.put("currency", request.currency());
            body.put("amount", amount);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("构建微信退款 JSON 失败", e);
        }
    }

    // ====== 内部方法 ======

    /**
     * 构建微信 App 下单请求 JSON。
     *
     * <p>参考：<a href="https://pay.weixin.qq.com/docs/merchant/apis/in-app-payment/direct-jsons/app-prepay.html">
     *     App下单</a>
     */
    private String buildOrderBodyJson(CreateOrderRequest request, String appId, String mchId, String notifyUrl) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appid", appId);
            body.put("mchid", mchId);
            body.put("description", truncate(request.productName(), 127));
            body.put("out_trade_no", request.orderId());
            body.put("notify_url", notifyUrl);

            // attach：透传字段，回调时原样返回
            if (request.attachJson() != null) {
                body.put("attach", truncate(request.attachJson(), 128));
            }

            // amount（必填）
            Map<String, Object> amount = new LinkedHashMap<>();
            amount.put("total", request.amountCents());
            amount.put("currency", request.currency());
            body.put("amount", amount);

            // scene_info（App 场景不是必填，但带上 payer_client_ip 有利于风控）
            if (request.clientIp() != null && !request.clientIp().isBlank()) {
                Map<String, Object> sceneInfo = new LinkedHashMap<>();
                sceneInfo.put("payer_client_ip", request.clientIp());
                body.put("scene_info", sceneInfo);
            }

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("构建微信下单 JSON 失败", e);
        }
    }

    /**
     * 尝试解析微信 API 的错误响应体，提取 message 字段。
     */
    private String parseWechatErrorMessage(String respBody) {
        if (respBody == null || respBody.isBlank()) return "未知错误";
        try {
            Map<String, Object> map = objectMapper.readValue(respBody, new TypeReference<>() {});
            Object msg = map.get("message");
            Object code = map.get("code");
            return (code != null ? code + " " : "") + (msg != null ? msg : respBody);
        } catch (Exception e) {
            return respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
        }
    }

    private String requireConfig(String key, String label) {
        String value = sysConfigService.getValue(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING,
                    "微信支付配置缺失：" + label + "（" + key + "）");
        }
        return value;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
    private static boolean httpsUrl(String value) {
        try { var uri = java.net.URI.create(value); return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && uri.getRawQuery() == null && uri.getRawFragment() == null; }
        catch (Exception e) { return false; }
    }

}
