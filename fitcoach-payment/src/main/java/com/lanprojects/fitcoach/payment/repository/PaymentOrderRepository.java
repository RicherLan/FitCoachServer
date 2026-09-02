package com.lanprojects.fitcoach.payment.repository;

import com.lanprojects.fitcoach.common.client.AppFlavor;
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
     * Admin 按 flavor 筛选（阶段 4 波 2）。传 {@code null} 会用 IS NULL 匹配 —— 但 Spring Data 派生方法
     * 不支持 null 参数自动转 IS NULL，所以 null 场景由 Service 层显式走 {@link #findByAppFlavorIsNullOrderByCreatedAtDesc}。
     */
    Page<PaymentOrder> findByAppFlavorOrderByCreatedAtDesc(AppFlavor appFlavor, Pageable pageable);

    /** Admin 查"未标注 flavor"（历史订单 / Postman / 老客户端）的订单 */
    Page<PaymentOrder> findByAppFlavorIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** Admin 按状态 + flavor 双条件筛选 */
    Page<PaymentOrder> findByStatusAndAppFlavorOrderByCreatedAtDesc(
            OrderStatus status, AppFlavor appFlavor, Pageable pageable);

    /** Admin 按状态 + flavor=NULL 双条件筛选 */
    Page<PaymentOrder> findByStatusAndAppFlavorIsNullOrderByCreatedAtDesc(
            OrderStatus status, Pageable pageable);

    /**
     * 待清理的过期未支付订单（用于定时任务关闭超时订单）。
     * 当前没用到 Pageable，限制由调用方 chunk 处理。
     */
    @Query("SELECT o FROM PaymentOrder o WHERE o.status = :status AND o.createdAt < :before")
    List<PaymentOrder> findStaleByStatusBefore(OrderStatus status, LocalDateTime before);

    /**
     * 最近一段时间内 status=PAID 的订单（按 paidAt 升序），用于会员激活补偿任务校验对应的会员是否已激活。
     */
    List<PaymentOrder> findByStatusAndPaidAtAfterOrderByPaidAtAsc(OrderStatus status, LocalDateTime since);

    /**
     * 是否有该套餐的订单（admin 删除套餐时用，有订单则不允许删除）
     */
    boolean existsByPlanCode(String planCode);
}
