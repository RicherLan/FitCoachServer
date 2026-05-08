package com.lanprojects.fitcoach.payment.repository;

import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    /** 按通道凭证号查（回调里确认订单时用） */
    Optional<PaymentOrder> findByChannelAndChannelTransactionId(PaymentChannel channel, String channelTransactionId);

    /** 用户订单分页（个人订单中心 / admin 用户订单查询） */
    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Admin 全量分页 */
    Page<PaymentOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Admin 按状态筛选 */
    Page<PaymentOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /**
     * 待清理的过期未支付订单（用于定时任务关闭超时订单）。
     * 当前没用到 Pageable，限制由调用方 chunk 处理。
     */
    @Query("SELECT o FROM PaymentOrder o WHERE o.status = :status AND o.createdAt < :before")
    List<PaymentOrder> findStaleByStatusBefore(OrderStatus status, LocalDateTime before);

    /**
     * 是否有该套餐的订单（admin 删除套餐时用，有订单则不允许删除）
     */
    boolean existsByPlanCode(String planCode);
}
