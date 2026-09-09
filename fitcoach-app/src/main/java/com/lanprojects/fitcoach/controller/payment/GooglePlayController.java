package com.lanprojects.fitcoach.controller.payment;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.controller.payment.dto.GoogleVerifyRequest;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.payment.provider.google.GooglePlayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Google Play 控制器（波 4 骨架）—— 客户端验单 + RTDN 通知回调。
 *
 * <ul>
 *   <li>{@code POST /api/payment/google/verify}：客户端 Google Play Billing 购买成功后验单 → markPaid；</li>
 *   <li>{@code POST /api/payment/notify/google}：RTDN（Cloud Pub/Sub 推送）回调，处理撤单/退款。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "客户端-Google Play", description = "Google Play 验单 + RTDN 通知回调")
@RestController
@RequiredArgsConstructor
public class GooglePlayController {

    private final GooglePlayService googlePlayService;
    private final AuthSupport auth;

    /**
     * 客户端 Google Play 验单 —— Billing 购买成功后提交 purchaseToken，服务端验证后把订单置 PAID。
     */
    @PostMapping("/api/payment/google/verify")
    public Result<Void> verify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody GoogleVerifyRequest req) {
        Long userId = auth.requireUserId(authorization);
        log.info("[google-play] 收到验单请求 userId={} orderId={}", userId, req.getOrderId());
        googlePlayService.verifyAndComplete(req.getOrderId(), req.getPurchaseToken(), req.getProductId());
        return Result.success();
    }

    /**
     * RTDN 回调（Google Cloud Pub/Sub 推送，无 token）。
     *
     * <p>请求体形如 {@code { "message": { "data": "<base64>", "messageId": "..." }, "subscription": "..." }}。
     * Pub/Sub 要求 2xx ack；处理异常也返回 200 避免无限重投（失败靠对账兜底）。
     */
    @PostMapping("/api/payment/notify/google")
    public Map<String, String> rtdn(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Object messageObj = body == null ? null : body.get("message");
            if (messageObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) messageObj;
                Object data = message.get("data");
                googlePlayService.handleRtdn(data == null ? null : data.toString());
            } else {
                log.warn("[google-play] RTDN 回调缺少 message 字段");
            }
        } catch (Exception e) {
            log.error("[google-play] RTDN 处理异常", e);
        }
        return Map.of("code", "SUCCESS");
    }
}
