package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.common.event.PaymentRefundedEvent;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.provider.*;
import com.lanprojects.fitcoach.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

/** 先提交退款号及金额预占，再调用通道；未知结果保留 PENDING，以原退款号重试。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentOrderRepository orderRepository;
    private final RefundOrderRepository refundRepository;
    private final PaymentChannelRouter channelRouter;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate tx() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
    private PaymentOrder lockOrder(String id) {
        return orderRepository.findLockedByOrderId(id)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
    }
    private record Reservation(RefundOrder refund, PaymentOrder order, boolean created) {}

    public RefundOrder refund(String orderId, Integer refundCents, String reason, String operator) {
        var reservation = tx().execute(status -> {
            var order = lockOrder(orderId);
            if (order.getStatus() != OrderStatus.PAID) {
                throw new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE);
            }
            var pending = refundRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                    .filter(r -> r.getStatus() == RefundStatus.PENDING).findFirst();
            // 一个订单同一时间只允许一笔在途申请；重复点击返回同一退款单，不再扣款。
            if (pending.isPresent()) { return new Reservation(pending.get(), order, false); }
            long completed = refundRepository.sumAmountByOrderIdAndStatus(orderId, RefundStatus.COMPLETED);
            if (refundCents != null && refundCents < 0) { throw new BusinessException(ResultCode.REFUND_AMOUNT_INVALID); }
            int amount = refundCents == null || refundCents == 0
                    ? (int) (order.getAmountCents() - completed) : refundCents;
            if (amount <= 0) { throw new BusinessException(ResultCode.REFUND_AMOUNT_INVALID); }
            if (completed + amount > order.getAmountCents()) { throw new BusinessException(ResultCode.REFUND_EXCEEDS_ORDER); }
            var provider = channelRouter.require(order.getChannel());
            if (!provider.supportsActiveRefund() && order.getChannel() != PaymentChannel.MOCK) {
                throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "该通道需在支付平台退款，服务端等待已验证的平台通知");
            }
            var refund = newRefund(order, amount, reason, operator,
                    provider.supportsActiveRefund() ? RefundMode.ACTIVE : RefundMode.PASSIVE);
            refundRepository.saveAndFlush(refund);
            order.setRefundStatus(RefundStatus.PENDING);
            orderRepository.save(order);
            if (refund.getRefundMode() == RefundMode.PASSIVE) { complete(refund, order); }
            return new Reservation(refund, order, true);
        });
        if (!reservation.created() || reservation.refund().getStatus() == RefundStatus.COMPLETED) {
            return reservation.refund();
        }
        return send(reservation.refund(), reservation.order());
    }

    /** 超时/中断后用原 out_refund_no 重试，绝不生成第二笔退款号。 */
    public RefundOrder retryPending(String orderId, String refundNo) {
        var reservation = tx().execute(status -> {
            var order = lockOrder(orderId);
            var refund = refundRepository.findByRefundNo(refundNo)
                    .orElseThrow(() -> new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE));
            if (!orderId.equals(refund.getOrderId()) || refund.getRefundMode() != RefundMode.ACTIVE) {
                throw new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE);
            }
            if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.COMPLETED) {
                throw new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE);
            }
            return new Reservation(refund, order, false);
        });
        if (reservation.refund().getStatus() == RefundStatus.COMPLETED) { return reservation.refund(); }
        return send(reservation.refund(), reservation.order());
    }

    private RefundOrder send(RefundOrder refund, PaymentOrder order) {
        RefundResult result;
        try {
            result = channelRouter.require(order.getChannel()).refund(new RefundRequest(
                    refund.getRefundNo(), order.getOrderId(), order.getChannelTransactionId(),
                    refund.getAmountCents(), order.getAmountCents(), order.getCurrency(), refund.getReason()));
        } catch (Exception e) {
            // 网络超时、进程中断、无法判定的通道错误：钱可能已退，不能释放可退余额。
            tx().executeWithoutResult(status -> {
                lockOrder(order.getOrderId());
                var current = refundRepository.findByRefundNo(refund.getRefundNo()).orElseThrow();
                if (current.getStatus() == RefundStatus.PENDING) {
                    current.setFailReason("通道结果待确认，请以原退款号重试或等待回调");
                    refundRepository.save(current);
                }
            });
            log.warn("[refund] 通道结果未知 refundNo={}", refund.getRefundNo(), e);
            throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "退款结果待确认，原退款单已保留，请勿另建退款");
        }
        var updated = tx().execute(status -> {
            var currentOrder = lockOrder(order.getOrderId());
            var current = refundRepository.findByRefundNo(refund.getRefundNo()).orElseThrow();
            // 回调可能先于 HTTP 响应到达，不能把已完成退款退回处理中。
            if (current.getStatus() == RefundStatus.COMPLETED) { return current; }
            current.setChannelRefundId(result.channelRefundId());
            current.setExtraJson(result.rawPayload());
            if (result.success()) {
                current.setFailReason(null);
                if (result.synchronouslyCompleted()) { complete(current, currentOrder); }
                else { refundRepository.save(current); }
            } else {
                current.setStatus(RefundStatus.FAILED);
                current.setFailReason("通道明确拒绝退款");
                refundRepository.save(current);
                currentOrder.setRefundStatus(RefundStatus.FAILED);
                orderRepository.save(currentOrder);
            }
            return current;
        });
        if (!result.success()) { throw new BusinessException(ResultCode.REFUND_PROVIDER_ERROR, "通道拒绝退款，失败记录已保留"); }
        return updated;
    }

    /** 仅由验签、解密后的微信通知调用，以商户退款号定位，支持请求超时与回调早到。 */
    public void completeByRefundNo(String refundNo, String channelId, String orderId, long amount, String raw) {
        tx().executeWithoutResult(status -> {
            var order = lockOrder(orderId);
            var refund = refundRepository.findByRefundNo(refundNo)
                    .orElseThrow(() -> new BusinessException(ResultCode.REFUND_ORDER_NOT_REFUNDABLE));
            if (order.getChannel() != PaymentChannel.WECHAT || !orderId.equals(refund.getOrderId()) ||
                    channelId == null || channelId.isBlank() || amount != refund.getAmountCents().longValue() ||
                    (refund.getChannelRefundId() != null && !channelId.equals(refund.getChannelRefundId()))) {
                throw new BusinessException(ResultCode.REFUND_AMOUNT_INVALID);
            }
            if (refund.getStatus() == RefundStatus.COMPLETED) { return; }
            refund.setChannelRefundId(channelId);
            refund.setExtraJson(raw);
            complete(refund, order);
        });
    }

    private void complete(RefundOrder refund, PaymentOrder order) {
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setFailReason(null);
        refund.setCompletedAt(LocalDateTime.now());
        refundRepository.saveAndFlush(refund);
        long total = refundRepository.sumAmountByOrderIdAndStatus(order.getOrderId(), RefundStatus.COMPLETED);
        boolean fully = total >= order.getAmountCents();
        order.setRefundAmountCents((int) total);
        order.setRefundStatus(RefundStatus.COMPLETED);
        if (fully) { order.setStatus(OrderStatus.REFUNDED); order.setRefundedAt(LocalDateTime.now()); }
        orderRepository.save(order);
        eventPublisher.publishEvent(PaymentRefundedEvent.builder()
                .orderId(order.getOrderId()).refundNo(refund.getRefundNo()).userId(order.getUserId())
                .productType(order.getProductType()).productCode(order.getProductCode())
                .refundCents(refund.getAmountCents()).currency(order.getCurrency())
                .fullyRefunded(fully).refundedAt(LocalDateTime.now()).build());
    }

    public RefundOrder recordPassiveRefund(String orderId, Integer refundCents, String reason, String operator) {
        return tx().execute(status -> {
            var order = lockOrder(orderId);
            if (order.getStatus() != OrderStatus.PAID) { return null; }
            long completed = refundRepository.sumAmountByOrderIdAndStatus(orderId, RefundStatus.COMPLETED);
            int remaining = (int) (order.getAmountCents() - completed);
            int amount = refundCents == null || refundCents <= 0 ? remaining : Math.min(refundCents, remaining);
            if (amount <= 0) { return null; }
            var refund = newRefund(order, amount, reason, operator, RefundMode.PASSIVE);
            complete(refund, order);
            return refund;
        });
    }
    private RefundOrder newRefund(PaymentOrder order, int amount, String reason, String operator, RefundMode mode) {
        var refund = new RefundOrder();
        refund.setRefundNo("RF" + UUID.randomUUID().toString().replace("-", ""));
        refund.setOrderId(order.getOrderId()); refund.setUserId(order.getUserId());
        refund.setChannel(order.getChannel()); refund.setRefundMode(mode);
        refund.setAmountCents(amount); refund.setCurrency(order.getCurrency());
        refund.setStatus(RefundStatus.PENDING); refund.setReason(reason); refund.setOperator(operator);
        return refund;
    }
    public List<RefundOrder> listByOrderId(String orderId) {
        return refundRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }
}
