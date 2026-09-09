package com.lanprojects.fitcoach.payment.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退款单 —— 一次退款操作的事实凭证。<b>独立于 {@link PaymentOrder} 的退款流水表</b>。
 *
 * <p><b>为什么用独立表而非在 payment_order 上加字段</b>：
 * <ul>
 *   <li><b>支持部分退款 / 多次退款</b>：一个订单可对应多条退款单（累计不超原额）；</li>
 *   <li><b>审计清晰</b>：每次退款的发起人、金额、时间、通道退款号都独立留痕；</li>
 *   <li><b>统一两种模式</b>：主动退款（微信调 API）和被动退款（Apple 通知）都用这张表记录；</li>
 *   <li>{@code payment_order.refund_status} / {@code refund_amount_cents} 仍保留为「聚合视图」
 *       （该订单退款总状态 / 累计退款额），由 RefundService 在每次退款后回写。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refund_order", indexes = {
        @Index(name = "uk_refund_order_no", columnList = "refund_no", unique = true),
        @Index(name = "idx_refund_order_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_order_status", columnList = "status"),
        @Index(name = "idx_refund_order_channel_refund_id", columnList = "channel_refund_id")
})
public class RefundOrder extends BaseEntity {

    /**
     * 退款单号（业务唯一标识，不暴露主键）。生成规则同 orderId：
     * 时间戳 + userId 后 4 位 + 4 位随机数，前缀 "RF"。例：RF20251115123456_8888_1234
     */
    @Column(name = "refund_no", nullable = false, length = 64)
    private String refundNo;

    /** 关联的支付订单业务号（{@link PaymentOrder#getOrderId()}） */
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    /** 退款用户 id（冗余自订单，便于按用户查退款） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 支付通道（冗余自订单，便于对账 / 决定退款走哪个 Provider） */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private PaymentChannel channel;

    /** 退款模式：ACTIVE 主动调通道 API / PASSIVE 平台通知或线下记账 */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", nullable = false, length = 16)
    private RefundMode refundMode;

    /** 本次退款金额（最小货币单位：分 / 美分）。部分退款时小于订单金额 */
    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    /** 币种 ISO 4217（CNY / USD），冗余自订单 */
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    /** 退款状态：PENDING 处理中 / COMPLETED 已完成 / FAILED 失败（复用 {@link RefundStatus}，不用 NONE） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundStatus status = RefundStatus.PENDING;

    /** 通道侧退款单号（微信 refund_id / Google refund id），主动退款成功后写入；被动/线下可空 */
    @Column(name = "channel_refund_id", length = 128)
    private String channelRefundId;

    /** 退款原因（必填，审计用） */
    @Column(name = "reason", length = 255)
    private String reason;

    /**
     * 发起人：admin 后台退款为操作员用户名；平台通知（Apple/Google）为 {@code "SYSTEM"}；
     * 定时对账触发为 {@code "RECONCILE"}。
     */
    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    /** 失败原因（status=FAILED 时填，便于排查通道退款失败） */
    @Column(name = "fail_reason", length = 255)
    private String failReason;

    /** 退款完成时间（status 切到 COMPLETED 时写入） */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 扩展字段（JSON）：通道退款返回的完整 payload，用于审计 / 重放 */
    @Lob
    @Column(name = "extra_json", columnDefinition = "TEXT")
    private String extraJson;
}
