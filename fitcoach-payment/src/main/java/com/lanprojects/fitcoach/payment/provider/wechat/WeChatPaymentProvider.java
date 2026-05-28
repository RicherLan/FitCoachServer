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

    /** HTTP 请求超时：连接 5s / 读取 10s */
    private static final int CONNECT_TIMEOUT = 5_000;
    private static final int READ_TIMEOUT = 10_000;

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

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
                && notBlank(sysConfigService.getValue(PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY));
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
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(bodyJson)
                .timeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .execute()) {

            int status = response.getStatus();
            String respBody = response.body();
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
            body.put("description", truncate(request.planDisplayName(), 127));
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
}
