package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.common.event.PaymentRefundedEvent;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.entity.RefundMode;
import com.lanprojects.fitcoach.payment.entity.RefundOrder;
import com.lanprojects.fitcoach.payment.entity.RefundStatus;
import com.lanprojects.fitcoach.payment.provider.PaymentChannelProvider;
import com.lanprojects.fitcoach.payment.provider.RefundRequest;
import com.lanprojects.fitcoach.payment.provider.RefundResult;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.repository.RefundOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款核心服务（波 1）—— 编排「校验 / 建退款单 / 通道退款 / 回写订单 / 发退款事件」。
 *
 * <p><b>两种退款模式（见 {@link RefundMode}）统一编排</b>：
 * <ul>
 *   <li>{@code ACTIVE}：通道支持主动退款（微信/支付宝/Google/Stripe）→ 调 {@link PaymentChannelProvider#refund}；</li>
 *   <li>{@code PASSIVE}：通道不支持主动退款（Apple IAP）或线下人工 → 仅记账 + 撤权益。</li>
 * </ul>
 * 模式由 Provider 的 {@link PaymentChannelProvider#supportsActiveRefund()} 自动决定，调用方无需关心。
 *
 * <p><b>去业务化</b>：与 PaymentService 一致，退款完成后只发 {@link PaymentRefundedEvent}
 * （带 productType/productCode），业务模块自行认领撤权益，payment 不认识"会员"。
 *
 * <p><b>可复用</b>：整个退款子系统（本类 + RefundOrder + refund_order 表 + 事件）搬到其它 App 原样可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final DateTimeFormatter REFUND_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentOrderRepository orderRepository;
    private final RefundOrderRepository refundRepository;
    private final PaymentChannelRouter channelRouter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发起退款（统一入口）。admin 后台主动退款、系统对账触发都走这里。
     *
     * <p>流程：校验订单可退 → 校验累计不超原额 → 按 Provider 能力决定模式 → 建退款单 PENDING →
     * ACTIVE 调通道 API / PASSIVE 直接记账 → 退款完成回写订单 + 发事件。
     *
     * @param orderId     原支付订单业务号
     * @param refundCents 本次退款金额（分）；{@code null} 或 ≤0 表示全额退（订单剩余可退额）
     * @param reason      退款原因（必填，审计）
     * @param operator    发起人（admin 用户名 / "SYSTEM" / "RECONCILE"）
     * @return 退款单（ACTIVE 异步通道可能是 PENDING，PASSIVE / 同步通道为 COMPLETED）
     */
    @Transactional
    public RefundOrder refund(String orderId, Integer refundCents, String reason, String operator) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));

        // 1. 只有 PAID 可退（全额退过的订单会变 REFUNDED，不再可退；部分退过仍为 PAID 可继续退）
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE,
                    "订单状态 " + order.getStatus() + " 不允许退款");
        }

        // 2. 金额校验：默认全额（订单剩余可退额）；累计不超原额
        long alreadyRefunded = refundRepository.sumAmountByOrderIdAndStatus(orderId, RefundStatus.COMPLETED);
        int amount = (refundCents == null || refundCents <= 0)
                ? (int) (order.getAmountCents() - alreadyRefunded)
                : refundCents;
        if (amount <= 0) {
            throw new BusinessException(ResultCode.REFUND_AMOUNT_INVALID);
        }
        if (alreadyRefunded + amount > order.getAmountCents()) {
            throw new BusinessException(ResultCode.REFUND_EXCEEDS_ORDER,
                    "已退 " + alreadyRefunded + " + 本次 " + amount + " 超过订单原额 " + order.getAmountCents());
        }

        // 3. 按 Provider 能力决定退款模式
        PaymentChannelProvider provider = channelRouter.require(order.getChannel());
        RefundMode mode = provider.supportsActiveRefund() ? RefundMode.ACTIVE : RefundMode.PASSIVE;

        // 4. 建退款单 PENDING
        RefundOrder refund = new RefundOrder();
        refund.setRefundNo(generateRefundNo(order.getUserId()));
        refund.setOrderId(orderId);
        refund.setUserId(order.getUserId());
        refund.setChannel(order.getChannel());
        refund.setRefundMode(mode);
        refund.setAmountCents(amount);
        refund.setCurrency(order.getCurrency());
        refund.setStatus(RefundStatus.PENDING);
        refund.setReason(reason);
        refund.setOperator(operator);
        refund = refundRepository.save(refund);
        log.info("[refund] 建退款单 refundNo={} orderId={} amount={} mode={} operator={}",
                refund.getRefundNo(), orderId, amount, mode, operator);

        // 5. ACTIVE 调通道 API；PASSIVE 直接记账完成
        if (mode == RefundMode.ACTIVE) {
            executeActiveRefund(provider, refund, order);
        } else {
            // PASSIVE：线下人工 / 平台通知场景 —— 直接记账完成 + 撤权益
            markRefundCompleted(refund, order);
        }
        return refund;
    }

    /** ACTIVE 模式：调通道退款 API，按结果置 COMPLETED / 留 PENDING（等异步回调）/ FAILED。 */
    private void executeActiveRefund(PaymentChannelProvider provider, RefundOrder refund, PaymentOrder order) {
        try {
            RefundResult result = provider.refund(new RefundRequest(
                    refund.getRefundNo(),
                    order.getOrderId(),
                    order.getChannelTransactionId(),
                    refund.getAmountCents(),
                    order.getAmountCents(),
                    order.getCurrency(),
                    refund.getReason()));

            refund.setChannelRefundId(result.channelRefundId());
            refund.setExtraJson(result.rawPayload());

            if (result.success() && result.synchronouslyCompleted()) {
                markRefundCompleted(refund, order);
            } else if (result.success()) {
                // 通道受理成功但退款异步处理中 —— 退款单留 PENDING，等退款结果回调（completeByChannelRefundId）
                refundRepository.save(refund);
                log.info("[refund] 通道受理成功待异步回调 refundNo={} channelRefundId={}",
                        refund.getRefundNo(), result.channelRefundId());
            } else {
                refund.setStatus(RefundStatus.FAILED);
                refund.setFailReason("通道受理失败");
                refundRepository.save(refund);
                throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "通道受理退款失败");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailReason(truncate(e.getMessage(), 255));
            refundRepository.save(refund);
            log.error("[refund] 通道退款异常 refundNo={} orderId={}", refund.getRefundNo(), order.getOrderId(), e);
            throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "退款失败：" + e.getMessage());
        }
    }

    /**
     * 通道退款结果异步回调入口（如微信退款结果通知）——把 PENDING 退款单置为 COMPLETED。
     * <p>幂等：已 COMPLETED 直接返回。
     */
    @Transactional
    public void completeByChannelRefundId(String channelRefundId, String rawPayload) {
        RefundOrder refund = refundRepository.findByChannelRefundId(channelRefundId).orElse(null);
        if (refund == null) {
            log.warn("[refund] 退款回调找不到退款单 channelRefundId={}", channelRefundId);
            return;
        }
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            log.info("[refund] 退款单已完成，幂等返回 refundNo={}", refund.getRefundNo());
            return;
        }
        PaymentOrder order = orderRepository.findByOrderId(refund.getOrderId())
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (rawPayload != null) {
            refund.setExtraJson(rawPayload);
        }
        markRefundCompleted(refund, order);
    }

    /** 退款完成：置退款单 COMPLETED + 回写订单聚合退款状态 + 发退款事件。 */
    private void markRefundCompleted(RefundOrder refund, PaymentOrder order) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(LocalDateTime.now());
        refundRepository.save(refund);

        // 回写订单聚合视图：累计退款额 + 退款状态；全额退完切主状态 REFUNDED
        long totalRefunded = refundRepository.sumAmountByOrderIdAndStatus(order.getOrderId(), RefundStatus.COMPLETED);
        boolean fully = totalRefunded >= order.getAmountCents();
        order.setRefundAmountCents((int) totalRefunded);
        order.setRefundStatus(fully ? RefundStatus.COMPLETED : RefundStatus.PENDING);
        if (fully) {
            order.setStatus(OrderStatus.REFUNDED);
            order.setRefundedAt(LocalDateTime.now());
        }
        orderRepository.save(order);

        // 发退款事件（AFTER_COMMIT 由监听方决定；此处在事务内 publish，Spring 会在 commit 后投递给 @TransactionalEventListener）
        eventPublisher.publishEvent(PaymentRefundedEvent.builder()
                .orderId(order.getOrderId())
                .refundNo(refund.getRefundNo())
                .userId(order.getUserId())
                .productType(order.getProductType())
                .productCode(order.getProductCode())
                .refundCents(refund.getAmountCents())
                .currency(order.getCurrency())
                .fullyRefunded(fully)
                .refundedAt(LocalDateTime.now())
                .build());

        log.info("[refund] 退款完成 refundNo={} orderId={} amount={} 累计={} fully={} 已发退款事件",
                refund.getRefundNo(), order.getOrderId(), refund.getAmountCents(), totalRefunded, fully);
    }

    /** 某订单的退款单列表（admin 订单详情展示退款历史用） */
    public List<RefundOrder> listByOrderId(String orderId) {
        return refundRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    // ====== 内部 ======

    /** 退款单号生成：RF + 时间戳 + userId 后 4 位 + 4 位随机数 */
    private String generateRefundNo(Long userId) {
        String suffix = String.format("%04d", userId == null ? 0 : Math.abs(userId.intValue()) % 10000);
        String rand = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return "RF" + LocalDateTime.now().format(REFUND_NO_FORMAT) + suffix + rand;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
