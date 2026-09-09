package com.lanprojects.fitcoach.payment.repository;

import com.lanprojects.fitcoach.payment.entity.RefundOrder;
import com.lanprojects.fitcoach.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 退款单仓储。
 */
public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {

    Optional<RefundOrder> findByRefundNo(String refundNo);

    /** 某订单的所有退款单（按创建时间倒序），订单详情 / 退款历史展示用 */
    List<RefundOrder> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /** 按通道退款号反查（通道退款结果异步回调时用） */
    Optional<RefundOrder> findByChannelRefundId(String channelRefundId);

    /**
     * 某订单在指定状态下的累计退款金额（分），用于「本次退款 + 已退 ≤ 订单原额」的防超额校验。
     * <p>无匹配记录时返回 0。
     */
    @Query("SELECT COALESCE(SUM(r.amountCents), 0) FROM RefundOrder r "
            + "WHERE r.orderId = :orderId AND r.status = :status")
    long sumAmountByOrderIdAndStatus(String orderId, RefundStatus status);
}
