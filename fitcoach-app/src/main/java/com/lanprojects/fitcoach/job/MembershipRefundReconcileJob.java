package com.lanprojects.fitcoach.job;

import com.lanprojects.fitcoach.membership.service.MembershipEntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** composition root 对账：退款已落库但进程退出/事件处理失败时补偿权益，无时间窗口遗漏。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MembershipRefundReconcileJob {
    private final JdbcTemplate jdbc;
    private final MembershipEntitlementService entitlements;
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void reconcile() {
        var orders = jdbc.queryForList("""
                SELECT o.order_id, o.user_id FROM payment_order o
                WHERE o.status = 'REFUNDED' AND o.product_type = 'MEMBERSHIP'
                AND NOT EXISTS (SELECT 1 FROM membership_entitlement e WHERE e.order_id = o.order_id AND e.revoked = true)
                ORDER BY o.id ASC LIMIT 100
                """);
        for (var order : orders) {
            try { entitlements.refund(((Number) order.get("user_id")).longValue(), (String) order.get("order_id")); }
            catch (Exception e) { log.error("[membership-refund] 对账失败 orderId={}", order.get("order_id"), e); }
        }
    }
}
