package com.lanprojects.fitcoach.payment.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付订单 — 一切支付/退款的事实凭证。
 *
 * <p><b>核心设计</b>：
 * <ul>
 *   <li><b>order_id 业务订单号</b>：自己生成的雪花/时间戳串，user_id 无关，{@code unique}，所有外部引用都用它（包括微信
 *       的 out_trade_no、回调日志关联），不暴露内部主键 id；</li>
 *   <li><b>套餐快照</b>：plan_code + plan_snapshot_name + amount_cents 三者都冗余在订单上，确保套餐改名/调价
 *       后历史订单展示不变；</li>
 *   <li><b>金额单位</b>：{@code amount_cents} 永远是最小货币单位（CNY 分 / USD 美分），杜绝浮点；</li>
 *   <li><b>时间</b>：所有时间字段 {@link Instant}（UTC），客户端按本地时区格式化；</li>
 *   <li><b>channel_transaction_id</b>：通道侧凭证（微信 transaction_id / Apple transaction_id），
 *       回调里收到后写入，便于后续退款/对账；</li>
 *   <li><b>extra_json</b>：@Lob 大字段存通道返回的完整 payload（如 Apple 的整个 receipt），用于事后审计/重放。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_order", indexes = {
        @Index(name = "uk_payment_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_payment_order_user", columnList = "user_id"),
        @Index(name = "idx_payment_order_status", columnList = "status"),
        @Index(name = "idx_payment_order_channel_txn", columnList = "channel_transaction_id")
})
public class PaymentOrder extends BaseEntity {

    /**
     * 业务订单号（外部唯一标识，不暴露内部主键）。生成规则：
     * 时间戳(yyyyMMddHHmmss) + userId 后 4 位 + 4 位随机数。例：20251115123456_8888_1234
     * <br/>类型 String 而非 Long，为了：a) 包含语义；b) 兼容微信 out_trade_no 的字符串约束。
     */
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    /** 下单用户 id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐 code（业务 key），与 membership_plan.plan_code 一致 */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    /**
     * 下单时套餐显示名快照。即使后续套餐改名，该订单仍显示购买当时的名字（用户体验/客服查询友好）。
     */
    @Column(name = "plan_snapshot_name", nullable = false, length = 64)
    private String planSnapshotName;

    /**
     * 支付通道
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private PaymentChannel channel;

    /**
     * 下单时的客户端平台（"android" / "ios"），从 ClientContext 取并落盘，便于按平台对账。
     */
    @Column(name = "client_platform", length = 16)
    private String clientPlatform;

    /**
     * 实付金额（最小货币单位，分/美分）。下单时根据 channel 决定币种：
     * <ul>
     *   <li>WECHAT/ALIPAY/MOCK → CNY 分；</li>
     *   <li>APPLE_IAP/GOOGLE_PLAY → USD 美分（参考价；实际苹果会按本地汇率收，回调里以 receipt 为准）。</li>
     * </ul>
     */
    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    /** 币种 ISO 4217 代码：CNY / USD */
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    /** 主状态机 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    /** 退款子状态（默认 NONE） */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 16)
    private RefundStatus refundStatus = RefundStatus.NONE;

    /**
     * 通道预支付号（微信 prepay_id / Apple 不需要）。下单时由 Provider 返回，传给客户端拉起支付。
     */
    @Column(name = "channel_prepay_id", length = 128)
    private String channelPrepayId;

    /**
     * 通道支付凭证号（微信 transaction_id / Apple transaction_id），由回调写入。
     * 退款/对账查询通道侧时使用。
     */
    @Column(name = "channel_transaction_id", length = 128)
    private String channelTransactionId;

    /** 支付成功时间（status 切到 PAID 时写入） */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 订单关闭时间（status 切到 CLOSED/FAILED 时写入） */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 退款完成时间 */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** 退款金额（最小货币单位）。部分退款时小于 amountCents */
    @Column(name = "refund_amount_cents")
    private Integer refundAmountCents;

    /** 失败/取消原因（人类可读，便于排查） */
    @Column(name = "fail_reason", length = 255)
    private String failReason;

    /**
     * 扩展字段（JSON）：通道返回的完整 payload，比如 Apple 的整个 receipt 全文。
     * 用 @Lob 而非 TEXT，确保 H2/MySQL 都能正常映射。
     */
    @Lob
    @Column(name = "extra_json", columnDefinition = "TEXT")
    private String extraJson;
}
