package com.lanprojects.fitcoach.payment.provider.google;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import com.lanprojects.fitcoach.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Google Play 验单 + RTDN 通知服务（波 4 骨架）。
 *
 * <p><b>验单</b>：客户端 Google Play Billing 购买成功拿 purchaseToken，调 {@code /api/payment/google/verify}，
 * 服务端验证后 markPaid。
 *
 * <p><b>RTDN（Real-time Developer Notifications）</b>：Google 通过 Cloud Pub/Sub 推送购买/退款事件，
 * 消息体 {@code { message: { data: base64(DeveloperNotification) } }}。本服务处理 voidedPurchaseNotification
 * （撤单/退款）→ 调 {@link RefundService#recordPassiveRefund} 记账 + 撤权益。
 *
 * <p><b>⚠️ 生产必须补全（当前骨架，等 Google Play Console + Service Account）</b>：
 * {@link #verifyAndComplete} 的真实校验需调 Google Play Developer API
 * {@code purchases.products.get} 验证 purchaseState；需用 Service Account 凭证换 OAuth token +
 * AndroidPublisher client。当前仅做存在性 + 防重放校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlayService {

    private final SysConfigService sysConfigService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final PaymentOrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== 客户端验单 ======

    /**
     * 验证 Google Play 购买并完成订单。
     *
     * @param orderId       我方业务订单号
     * @param purchaseToken Google Play Billing 购买凭证
     * @param productId     Google Play 商品 id（用于调 API 校验，骨架期仅记录）
     */
    @Transactional
    public void verifyAndComplete(String orderId, String purchaseToken, String productId) {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.GOOGLE_PLAY_ENABLED, false)) {
            throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING, "Google Play 未启用");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID, "purchaseToken 为空");
        }
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));

        // TODO(生产必做)：调 Google Play Developer API purchases.products.get(packageName, productId, purchaseToken)
        //   校验 purchaseState==0(purchased)、acknowledgementState 等；需 Service Account 换 OAuth token。
        log.info("[google-play] 验单（骨架）orderId={} productId={} tokenLen={}",
                orderId, productId, purchaseToken.length());

        // 防重放：purchaseToken 作为通道凭证号查重
        orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.GOOGLE_PLAY, purchaseToken)
                .ifPresent(existing -> {
                    if (!existing.getOrderId().equals(orderId)) {
                        log.error("[google-play] purchaseToken 已被使用 原订单={} 本订单={}",
                                existing.getOrderId(), orderId);
                        throw new BusinessException(ResultCode.PAYMENT_RECEIPT_ALREADY_USED);
                    }
                });

        // Google Play 实际收款金额由 Google 按各地货币决定，不做本地金额校验（传 null）
        paymentService.markPaid(orderId, null, purchaseToken);
        log.info("[google-play] 验单成功 orderId={}", orderId);
    }

    // ====== RTDN 通知 ======

    /**
     * 处理 RTDN 推送的 DeveloperNotification（已 base64 解出的那一层由调用方传入亦可；
     * 此处接收 message.data 的 base64 字符串）。当前仅处理 voidedPurchaseNotification（撤单/退款）。
     *
     * @param messageDataBase64 Pub/Sub message.data（base64 编码的 DeveloperNotification JSON）
     */
    @Transactional
    public void handleRtdn(String messageDataBase64) {
        if (messageDataBase64 == null || messageDataBase64.isBlank()) {
            log.warn("[google-play] RTDN message.data 为空");
            return;
        }
        Map<String, Object> notification;
        try {
            String json = new String(Base64.getDecoder().decode(messageDataBase64), StandardCharsets.UTF_8);
            notification = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[google-play] RTDN 解码失败", e);
            return;
        }

        Object voidedObj = notification.get("voidedPurchaseNotification");
        if (!(voidedObj instanceof Map<?, ?>)) {
            // 其它类型（oneTimeProductNotification / subscriptionNotification）暂不处理
            log.info("[google-play] RTDN 非退款通知，忽略 keys={}", notification.keySet());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> voided = (Map<String, Object>) voidedObj;
        String purchaseToken = str(voided.get("purchaseToken"));
        if (purchaseToken == null) {
            log.warn("[google-play] RTDN voided 缺少 purchaseToken");
            return;
        }
        orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.GOOGLE_PLAY, purchaseToken)
                .ifPresentOrElse(
                        order -> {
                            // 平台已退款/撤单 → 强制 PASSIVE 记账 + 撤权益（不再调 Google 主动退款 API）
                            refundService.recordPassiveRefund(order.getOrderId(), null,
                                    "Google Play 撤单/退款（RTDN 通知）", "SYSTEM");
                            log.info("[google-play] RTDN 退款已处理 orderId={}", order.getOrderId());
                        },
                        () -> log.warn("[google-play] RTDN 退款找不到订单 tokenLen={}", purchaseToken.length()));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
