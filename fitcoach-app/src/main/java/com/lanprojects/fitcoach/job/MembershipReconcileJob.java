package com.lanprojects.fitcoach.job;

import com.lanprojects.fitcoach.membership.entity.UserMembership;
import com.lanprojects.fitcoach.membership.repository.UserMembershipRepository;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会员激活补偿定时任务 — 兜底处理"支付已成功但会员未激活"的异常订单。
 *
 * <p><b>问题背景</b>：
 * {@code MembershipService.onPaymentSucceeded()} 通过 Spring 事件 + {@code @Async} 异步消费支付成功事件。
 * 异步消费过程中若发生异常（DB 连接抖动 / 业务校验冲突 / 节点重启），异常会被 try/catch 吞掉，
 * 用户支付成功但会员未激活，此时只能靠人工客服干预。
 *
 * <p><b>本任务</b>：定期扫描最近一段时间内 status=PAID 的订单，
 * 对比 user_membership.lastOrderId 是否对应得上，不对应则补激活。
 *
 * <p><b>幂等保证</b>：
 * {@link MembershipService#activate} 内部 upsertMembership 会判断 lastOrderId 相同则短路，所以重复触发安全。
 *
 * <p><b>架构选择</b>：
 * 放在 fitcoach-app 模块（composition root），因为 membership 模块刻意不依赖 payment 模块（事件解耦），
 * 而补偿任务需要跨两个域查数据，唯有 app 层可以同时拿到。
 *
 * <p><b>多实例部署</b>：当前是单实例 {@code @Scheduled}，多实例部署时应上 ShedLock 防重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipReconcileJob {

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserMembershipRepository userMembershipRepository;
    private final MembershipService membershipService;

    /**
     * 扫描窗口（小时）：扫最近 2 小时的 PAID 订单。
     * 选 2h 的考量：异步激活通常秒级完成，2h 足够覆盖网络抖动/重启等异常；
     * 同时窗口越大每次扫描的数据越多，2h 是性能与覆盖率的折中。
     */
    private static final int SCAN_WINDOW_HOURS = 2;

    /**
     * 每 5 分钟跑一次。和上文 2 小时窗口配合：单次失败的订单最多 5 分钟内补上，
     * 即使任务本身挂掉，下一次跑仍能覆盖。
     */
    @Scheduled(fixedDelayString = "${membership.reconcile.interval-ms:300000}",
            initialDelayString = "${membership.reconcile.initial-delay-ms:60000}")
    public void reconcile() {
        LocalDateTime since = LocalDateTime.now().minusHours(SCAN_WINDOW_HOURS);
        List<PaymentOrder> recentPaid = paymentOrderRepository
                .findByStatusAndPaidAtAfterOrderByPaidAtAsc(OrderStatus.PAID, since);

        if (recentPaid.isEmpty()) {
            log.debug("[membership-reconcile] 最近 {}h 无 PAID 订单，跳过", SCAN_WINDOW_HOURS);
            return;
        }

        int repaired = 0;
        int suspicious = 0;
        int failed = 0;
        for (PaymentOrder order : recentPaid) {
            try {
                ReconcileDecision decision = decide(order);
                switch (decision) {
                    case OK:
                        break;
                    case AUTO_REPAIR:
                        log.warn("[membership-reconcile] 自动补激活 orderId={} userId={} planCode={} paidAt={}",
                                order.getOrderId(), order.getUserId(), order.getPlanCode(), order.getPaidAt());
                        membershipService.activate(order.getUserId(), order.getPlanCode(), order.getOrderId());
                        repaired++;
                        break;
                    case SUSPICIOUS_NEED_HUMAN:
                        // 不自动补：避免对"用户已买多个订单、本订单不是最近一笔"的场景误叠加会员
                        log.error("[membership-reconcile] ⚠️ 检测到可疑未激活订单，需人工核查 " +
                                        "orderId={} userId={} planCode={} paidAt={}，" +
                                        "建议 admin 后台手动激活后再处理",
                                order.getOrderId(), order.getUserId(), order.getPlanCode(), order.getPaidAt());
                        suspicious++;
                        break;
                }
            } catch (Exception e) {
                failed++;
                log.error("[membership-reconcile] 补偿激活失败 orderId={} userId={} planCode={}",
                        order.getOrderId(), order.getUserId(), order.getPlanCode(), e);
            }
        }

        if (repaired > 0 || failed > 0 || suspicious > 0) {
            log.warn("[membership-reconcile] 本轮处理完成 scanned={} repaired={} suspicious={} failed={}",
                    recentPaid.size(), repaired, suspicious, failed);
        } else {
            log.debug("[membership-reconcile] 本轮处理完成 scanned={}（全部一致）", recentPaid.size());
        }
    }

    /**
     * 三态决策：
     * <ul>
     *   <li>OK：已正确激活，无需处理；</li>
     *   <li>AUTO_REPAIR：明确漏激活（无会员记录 / lastOrderId 为空），可安全自动补；</li>
     *   <li>SUSPICIOUS_NEED_HUMAN：lastOrderId 与本订单不一致，但无法确认是否真的漏激活
     *       （用户可能购买了多个订单），避免误叠加，仅打 ERROR 让人工核查。</li>
     * </ul>
     */
    private ReconcileDecision decide(PaymentOrder order) {
        Optional<UserMembership> membership = userMembershipRepository.findByUserId(order.getUserId());

        if (membership.isEmpty()) {
            // 用户压根没会员记录 → 一定是漏激活
            return ReconcileDecision.AUTO_REPAIR;
        }

        String lastOrderId = membership.get().getLastOrderId();
        if (lastOrderId == null || lastOrderId.isBlank()) {
            // 有会员但 lastOrderId 为空（异常状态）→ 补激活
            return ReconcileDecision.AUTO_REPAIR;
        }

        if (lastOrderId.equals(order.getOrderId())) {
            // 本订单已激活
            return ReconcileDecision.OK;
        }

        // lastOrderId != orderId：可能是用户买了多个订单（先 A 后 B，lastOrderId=B 时扫到 A）
        // 也可能是本订单没消费到事件。无法自动判断，提示人工核查。
        return ReconcileDecision.SUSPICIOUS_NEED_HUMAN;
    }

    private enum ReconcileDecision { OK, AUTO_REPAIR, SUSPICIOUS_NEED_HUMAN }
}
