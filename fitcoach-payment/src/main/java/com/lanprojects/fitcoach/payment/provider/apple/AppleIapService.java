package com.lanprojects.fitcoach.payment.provider.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import com.lanprojects.fitcoach.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** StoreKit 签名凭证及 ASSN V2 通知验证，商品快照与订单 token 必须匹配。 */
@Service
@RequiredArgsConstructor
public class AppleIapService {
    private final SysConfigService sysConfigService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final PaymentOrderRepository orderRepository;
    private final AppleSignedDataVerifier verifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void verifyAndComplete(Long userId, String orderId, String signedTransaction) {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.APPLE_ENABLED, false)) {
            throw new BusinessException(ResultCode.PAYMENT_CHANNEL_DISABLED);
        }
        PaymentOrder order = orderRepository.findLockedByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) { throw new BusinessException(ResultCode.PAYMENT_ORDER_NOT_OWNED); }
        if (order.getChannel() != PaymentChannel.APPLE_IAP) { throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID); }
        var tx = verifier.transaction(signedTransaction);
        String productId;
        try { productId = objectMapper.readTree(order.getExtraJson()).path("productId").asText(); }
        catch (Exception e) { throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING, "订单缺少 Apple 商品快照"); }
        if (productId.isBlank() || !productId.equals(tx.getProductId()) ||
                tx.getTransactionId() == null || tx.getTransactionId().isBlank() ||
                tx.getRevocationDate() != null || tx.getPurchaseDate() == null ||
                !AppleSignedDataVerifier.accountToken(orderId).equals(tx.getAppAccountToken())) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID);
        }
        orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.APPLE_IAP, tx.getTransactionId())
                .filter(existing -> !existing.getOrderId().equals(orderId))
                .ifPresent(existing -> { throw new BusinessException(ResultCode.PAYMENT_RECEIPT_ALREADY_USED); });
        if (order.getChannelTransactionId() != null && !order.getChannelTransactionId().equals(tx.getTransactionId())) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_ALREADY_USED);
        }
        paymentService.markPaid(orderId, null, tx.getTransactionId());
    }

    @Transactional
    public void handleServerNotification(String signedPayload) {
        var notification = verifier.notification(signedPayload);
        if (notification.getNotificationType() != com.apple.itunes.storekit.model.NotificationTypeV2.REFUND) { return; }
        if (notification.getData() == null) { throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID); }
        var tx = verifier.transaction(notification.getData().getSignedTransactionInfo());
        if (tx.getTransactionId() == null || tx.getRevocationDate() == null) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID);
        }
        var order = orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.APPLE_IAP, tx.getTransactionId())
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.REFUNDED) { return; }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ResultCode.PAYMENT_ORDER_STATUS_INVALID);
        }
        refundService.recordPassiveRefund(order.getOrderId(), null, "Apple ASSN 已验签退款", "SYSTEM");
    }
}
