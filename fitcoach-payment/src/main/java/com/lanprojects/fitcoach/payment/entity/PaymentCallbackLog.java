package com.lanprojects.fitcoach.payment.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 支付通道回调日志 — 流水表，所有外部回调（微信异步通知 / Apple 服务器回调 / Mock 回调）落盘。
 *
 * <p><b>用途</b>：
 * <ol>
 *   <li>排查"用户已付款但没开会员"类问题：拿 order_id 查回调是不是收到了；</li>
 *   <li>幂等基础：同一 channel + channelTxnId 已成功处理过的回调直接返回 success 不再处理；</li>
 *   <li>合规审计：通道方可能要求保留若干个月的原始通知报文。</li>
 * </ol>
 *
 * <p><b>不删除原则</b>：本表只 INSERT 不 UPDATE 不 DELETE，便于事后追溯。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_callback_log", indexes = {
        @Index(name = "idx_pcl_order", columnList = "order_id"),
        @Index(name = "idx_pcl_channel_txn", columnList = "channel_transaction_id"),
        @Index(name = "idx_pcl_received_at", columnList = "createdAt")
})
public class PaymentCallbackLog extends BaseEntity {

    /** 关联的业务订单号（可能为 null：通道方推送了无法匹配的订单） */
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private PaymentChannel channel;

    /** 通道侧凭证号（微信 transaction_id / Apple transaction_id） */
    @Column(name = "channel_transaction_id", length = 128)
    private String channelTransactionId;

    /** 签名是否通过（false = 拒收，仍记录便于安全分析） */
    @Column(name = "sign_valid", nullable = false)
    private Boolean signValid = false;

    /** 处理是否成功（业务侧的处理结果，与签名校验独立） */
    @Column(name = "process_success", nullable = false)
    private Boolean processSuccess = false;

    /** 处理失败时的原因（人类可读） */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /**
     * 通道方原始 payload（微信是 XML，Apple 是 JSON）。@Lob 兼容 H2/MySQL。
     */
    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
