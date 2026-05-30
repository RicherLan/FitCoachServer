package com.lanprojects.fitcoach.payment.job;

import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付订单自动关闭 — 防止 PENDING 订单长期占着用户的待支付状态，
 * 也保证 PENDING / PAID 单一活跃订单的不变量（同一用户同一套餐只应有一个活跃 PENDING）。
 *
 * <p><b>触发场景</b>：用户下单后切走 App、拒绝支付、网络异常等都会导致 PENDING 订单残留。
 * 当前会员模块 / RN 客户端的 "重新下单" 都期望旧 PENDING 已经被关闭。
 *
 * <p><b>策略</b>：
 * <ul>
 *   <li>每 5 分钟扫一次，每次最多处理 200 条；</li>
 *   <li>关闭超过 {@code payment.order-expire.timeout-minutes}（默认 30 分钟）的 PENDING；</li>
 *   <li>{@code closeOrder} 内部对 status 做了二次校验（非 PENDING 直接 return），保证并发安全。</li>
 * </ul>
 *
 * <p><b>边界</b>：批处理失败不阻塞下一批 — 单条异常 catch 后继续；整体异常被 try/catch 吞掉，
 * 避免 Spring scheduler 因任务异常停止调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderExpireJob {

    /** 单次扫描最多关闭的订单数 — 防止单实例阻塞 */
    private static final int BATCH_LIMIT = 200;

    private final PaymentOrderRepository orderRepository;
    private final PaymentService paymentService;

    @Value("${payment.order-expire.timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(
            fixedDelayString = "${payment.order-expire.interval-ms:300000}",
            initialDelayString = "${payment.order-expire.initial-delay-ms:60000}"
    )
    public void runExpire() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
            List<PaymentOrder> stale = orderRepository.findStaleByStatusBefore(OrderStatus.PENDING, cutoff);
            if (stale.isEmpty()) {
                return;
            }

            int processed = Math.min(stale.size(), BATCH_LIMIT);
            int closed = 0;
            int failed = 0;
            for (int i = 0; i < processed; i++) {
                PaymentOrder order = stale.get(i);
                try {
                    paymentService.closeOrder(order.getOrderId(),
                            "超过 " + timeoutMinutes + " 分钟未支付，系统自动关闭");
                    closed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[payment-expire] 关闭超时订单失败 orderId={} reason={}",
                            order.getOrderId(), e.toString());
                }
            }
            log.info("[payment-expire] 扫描完成 totalStale={} processed={} closed={} failed={}",
                    stale.size(), processed, closed, failed);
        } catch (Exception e) {
            log.error("[payment-expire] 任务扫描异常", e);
        }
    }
}
