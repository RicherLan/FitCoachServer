package com.lanprojects.fitcoach.payment.provider.wechat;

import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.LinkedHashMap;
import java.util.Map;

/** 外部查单/关单在本地数据库事务之外执行；以已验签状态补偿丢失通知。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatOrderSyncService {
    private final WeChatPaymentProvider provider;
    private final WeChatCallbackHandler callbacks;
    private final PaymentService payments;
    private final PaymentOrderRepository orders;
    private final Map<String, Long> lastQueries = new LinkedHashMap<>();
    private synchronized boolean mayQuery(String id) {
        long now = System.currentTimeMillis();
        if (now - lastQueries.getOrDefault(id, 0L) < 5000) return false;
        lastQueries.put(id, now);
        if (lastQueries.size() > 2000) lastQueries.remove(lastQueries.keySet().iterator().next());
        return true;
    }
    public void refreshIfPending(PaymentOrder order) {
        if (order.getChannel() != PaymentChannel.WECHAT || order.getStatus() != OrderStatus.PENDING
                || !provider.isAvailable() || !mayQuery(order.getOrderId())) return;
        try {
            var transaction = provider.queryTransaction(order.getOrderId());
            if ("SUCCESS".equals(transaction.get("trade_state")) && !callbacks.applyVerifiedTransaction(transaction)) {
                log.warn("[wechat-query] 查单业务校验失败 orderId={}", order.getOrderId());
            }
        } catch (Exception e) { log.warn("[wechat-query] 暂未确认订单状态 orderId={}", order.getOrderId()); }
    }
    public void closeOrder(String orderId, String reason) {
        var order = payments.findByOrderId(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING) return;
        if (order.getChannel() == PaymentChannel.WECHAT) {
            var transaction = provider.queryTransaction(orderId);
            if ("SUCCESS".equals(transaction.get("trade_state"))) {
                if (!callbacks.applyVerifiedTransaction(transaction)) throw new IllegalStateException("支付结果校验失败");
                return;
            }
            if (!"CLOSED".equals(transaction.get("trade_state"))) provider.closeRemoteOrder(orderId);
        }
        payments.closeOrder(orderId, reason);
    }
    @Scheduled(fixedDelayString = "${payment.wechat.reconcile-ms:60000}", initialDelay = 60000)
    public void reconcilePending() {
        if (!provider.isAvailable()) return;
        orders.findTop20ByChannelAndStatusOrderByCreatedAtAsc(PaymentChannel.WECHAT, OrderStatus.PENDING)
                .forEach(this::refreshIfPending);
    }
}
