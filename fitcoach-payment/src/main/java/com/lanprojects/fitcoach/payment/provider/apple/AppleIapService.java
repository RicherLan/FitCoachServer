package com.lanprojects.fitcoach.payment.provider.apple;

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
 * Apple IAP 验单 + 服务器通知服务（波 3 骨架）。
 *
 * <p><b>IAP 支付模型</b>：客户端发起 + 服务端验单（与微信「服务端统一下单」相反）：
 * <ol>
 *   <li>客户端 StoreKit 购买成功拿到 signedTransaction（JWS，Apple 签名）；</li>
 *   <li>调 {@code POST /api/payment/apple/verify} 提交 orderId + signedTransaction；</li>
 *   <li>{@link #verifyAndComplete} 验签 + 解析 → 防重放 → markPaid → 发支付成功事件。</li>
 * </ol>
 *
 * <p><b>退款</b>：Apple 退款只能由用户向 Apple 申请（PASSIVE 模式），Apple 审核后通过
 * <b>App Store Server Notifications V2</b> 推送 REFUND 通知到 {@code /api/payment/notify/apple}，
 * 由 {@link #handleServerNotification} 反查订单 → 调 {@link RefundService}（因 AppleIAPProvider 不支持
 * 主动退款，RefundService 自动走 PASSIVE 记账 + 撤权益）。这与「Apple 无法主动退款」的现实完全契合。
 *
 * <p><b>⚠️ 生产必须补全（当前骨架，等苹果开发者账号）</b>：
 * {@link #decodeJwsPayload} 仅 Base64 解码 payload，<u>未验证 JWS 签名 + x5c 证书链</u>（Apple Root CA），
 * 生产环境必须补全，否则伪造凭证可绕过。建议接入官方
 * <a href="https://github.com/apple/app-store-server-library-java">app-store-server-library-java</a>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleIapService {

    private final SysConfigService sysConfigService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final PaymentOrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== 客户端验单 ======

    /**
     * 验证 signedTransaction 并完成订单。
     *
     * @param orderId           我方业务订单号（下单时 createOrder 占位生成）
     * @param signedTransaction 客户端 StoreKit 拿到的 JWS 交易凭证
     */
    @Transactional
    public void verifyAndComplete(String orderId, String signedTransaction) {
        if (!sysConfigService.getBoolValue(PaymentConfigKeys.APPLE_ENABLED, false)) {
            throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING, "Apple IAP 未启用");
        }
        if (signedTransaction == null || signedTransaction.isBlank()) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID, "signedTransaction 为空");
        }
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));

        AppleTransaction tx = parseTransaction(signedTransaction);

        // bundleId 一致性校验（防其它 App 凭证冒用）
        String expectBundleId = sysConfigService.getValue(PaymentConfigKeys.APPLE_BUNDLE_ID);
        if (expectBundleId != null && !expectBundleId.isBlank()
                && tx.bundleId() != null && !expectBundleId.equals(tx.bundleId())) {
            log.error("[apple-iap] bundleId 不匹配 expect={} actual={}", expectBundleId, tx.bundleId());
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID, "bundleId 不匹配");
        }

        // 防重放：同一 transactionId 不能用于不同订单
        if (tx.transactionId() != null) {
            orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.APPLE_IAP, tx.transactionId())
                    .ifPresent(existing -> {
                        if (!existing.getOrderId().equals(orderId)) {
                            log.error("[apple-iap] transactionId 已被使用 txnId={} 原订单={} 本订单={}",
                                    tx.transactionId(), existing.getOrderId(), orderId);
                            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_ALREADY_USED);
                        }
                    });
        }

        // IAP 实际收款金额由 Apple 按各地汇率决定，不做本地金额校验（传 null 跳过）
        paymentService.markPaid(orderId, null, tx.transactionId());
        log.info("[apple-iap] 验单成功 orderId={} transactionId={} productId={}",
                orderId, tx.transactionId(), tx.productId());
    }

    // ====== App Store Server Notifications V2 ======

    /**
     * 处理 ASSN V2 服务器通知（当前仅处理 REFUND；订阅续费等 DID_RENEW/EXPIRED 后续按需扩展）。
     *
     * @param signedPayload 通知外层 JWS
     */
    @Transactional
    public void handleServerNotification(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            log.warn("[apple-iap] ASSN 回调 signedPayload 为空");
            return;
        }
        Map<String, Object> notification = decodeJwsPayload(signedPayload);
        String notificationType = str(notification.get("notificationType"));
        log.info("[apple-iap] 收到 ASSN 通知 type={}", notificationType);

        if (!"REFUND".equals(notificationType)) {
            // 其它类型（订阅续费/过期/升降级等）暂不处理
            return;
        }

        Object dataObj = notification.get("data");
        if (!(dataObj instanceof Map<?, ?>)) {
            log.warn("[apple-iap] ASSN REFUND 缺少 data 字段");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        String signedTransactionInfo = str(data.get("signedTransactionInfo"));
        if (signedTransactionInfo == null) {
            log.warn("[apple-iap] ASSN REFUND 缺少 signedTransactionInfo");
            return;
        }

        AppleTransaction tx = parseTransaction(signedTransactionInfo);
        orderRepository.findByChannelAndChannelTransactionId(PaymentChannel.APPLE_IAP, tx.transactionId())
                .ifPresentOrElse(
                        order -> {
                            // 通知场景（平台已退款）统一走 recordPassiveRefund：强制 PASSIVE 记账 + 撤权益 + 幂等
                            refundService.recordPassiveRefund(order.getOrderId(), null, "Apple 用户退款（ASSN 通知）", "SYSTEM");
                            log.info("[apple-iap] ASSN 退款已处理 orderId={} txnId={}",
                                    order.getOrderId(), tx.transactionId());
                        },
                        () -> log.warn("[apple-iap] ASSN 退款找不到对应订单 txnId={}", tx.transactionId()));
    }

    // ====== 内部：JWS 解析 ======

    private AppleTransaction parseTransaction(String signedTransaction) {
        Map<String, Object> map = decodeJwsPayload(signedTransaction);
        return new AppleTransaction(
                str(map.get("transactionId")),
                str(map.get("originalTransactionId")),
                str(map.get("productId")),
                str(map.get("bundleId")));
    }

    /**
     * 解码 JWS（header.payload.signature）的 payload 段。
     * <p><b>⚠️ 生产必须补全</b>：用 header.x5c 证书链 + Apple Root CA 验证签名，当前<u>未验签</u>。
     */
    private Map<String, Object> decodeJwsPayload(String jws) {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID, "JWS 格式非法");
        }
        // TODO(生产必做)：验证 JWS 签名 + x5c 证书链（Apple Root CA）。当前仅解码 payload，未验签！
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[apple-iap] JWS payload 解码失败", e);
            throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID, "JWS 解析失败");
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** 从 signedTransaction 解析出的关键交易字段。 */
    private record AppleTransaction(String transactionId, String originalTransactionId,
                                    String productId, String bundleId) {
    }
}
