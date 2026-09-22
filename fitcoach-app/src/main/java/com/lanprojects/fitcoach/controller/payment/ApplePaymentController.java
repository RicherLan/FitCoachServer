package com.lanprojects.fitcoach.controller.payment;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.controller.payment.dto.AppleVerifyRequest;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.payment.provider.apple.AppleIapService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Apple IAP 控制器（波 3 骨架）—— 客户端验单 + App Store 服务器通知回调。
 *
 * <p>路径故意不加类级 {@code @RequestMapping}，两个端点各自声明完整路径以对齐既有约定：
 * <ul>
 *   <li>{@code POST /api/payment/apple/verify}：客户端 StoreKit 购买成功后验单 → markPaid；</li>
 *   <li>{@code POST /api/payment/notify/apple}：App Store Server Notifications V2 回调（退款等）。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "客户端-Apple IAP", description = "IAP 验单 + App Store 服务器通知回调")
@RestController
@RequiredArgsConstructor
public class ApplePaymentController {

    private final AppleIapService appleIapService;
    private final AuthSupport auth;

    /**
     * 客户端 IAP 验单 —— StoreKit 购买成功后提交 signedTransaction，服务端验签后把订单置 PAID。
     */
    @PostMapping("/api/payment/apple/verify")
    public Result<Void> verify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AppleVerifyRequest req) {
        Long userId = auth.requireUserId(authorization);
        log.info("[apple-iap] 收到验单请求 userId={} orderId={}", userId, req.getOrderId());
        appleIapService.verifyAndComplete(userId, req.getOrderId(), req.getSignedTransaction());
        return Result.success();
    }

    /**
     * App Store Server Notifications V2 回调 —— Apple 主动推送（无 token，靠 JWS 验签保证真实性）。
     *
     * <p>请求体形如 {@code {"signedPayload": "<JWS>"}}。Apple 要求 2xx 即视为成功；
     * 处理异常也返回 200，避免 Apple 无限重试（失败靠后续对账兜底）。
     */
    @PostMapping("/api/payment/notify/apple")
    public org.springframework.http.ResponseEntity<Map<String, String>> serverNotification(@RequestBody Map<String, String> body) {
        String signedPayload = body == null ? null : body.get("signedPayload");
        try {
            appleIapService.handleServerNotification(signedPayload);
        } catch (Exception e) {
            log.error("[apple-iap] ASSN 处理异常", e);
            return org.springframework.http.ResponseEntity.status(503).body(Map.of("code", "RETRY"));
        }
        return org.springframework.http.ResponseEntity.ok(Map.of("code", "SUCCESS"));
    }
}
