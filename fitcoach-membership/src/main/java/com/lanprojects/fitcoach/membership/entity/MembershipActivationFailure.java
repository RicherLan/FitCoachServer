package com.lanprojects.fitcoach.membership.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会员激活失败记录 — 配合 {@link com.lanprojects.fitcoach.membership.job.MembershipActivationRetryJob}
 * 做异步补偿，避免支付成功但因 @Async 异常导致会员未激活的资损风险。
 *
 * <p><b>背景</b>：{@code MembershipService.onPaymentSucceeded} 是 {@code @Async + @TransactionalEventListener}，
 * 一旦内部 activate 抛异常会被 try/catch 吞掉（这样设计是必须的，因为事件发布者已 commit），
 * 监听器内的失败如果没有持久化记录，就会永久丢失。
 *
 * <p><b>状态机</b>：
 * <ul>
 *   <li>{@link Status#PENDING}：待重试，由 retry job 周期性扫描；</li>
 *   <li>{@link Status#SUCCESS}：重试激活成功（或人工激活后由 service 标记）；</li>
 *   <li>{@link Status#PERMANENT_FAIL}：超过最大重试次数仍失败，需要人工介入。</li>
 * </ul>
 *
 * <p><b>幂等约束</b>：同一 {@code orderId} 在表中只能存在一条记录（unique 索引），
 * 防止重试任务自身重入或事件重投导致重复插入。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_activation_failure", indexes = {
        @Index(name = "uk_activation_failure_order", columnList = "order_id", unique = true),
        @Index(name = "idx_activation_failure_status", columnList = "status"),
        @Index(name = "idx_activation_failure_next_retry", columnList = "next_retry_at")
})
public class MembershipActivationFailure extends BaseEntity {

    public enum Status {
        PENDING,
        SUCCESS,
        PERMANENT_FAIL
    }

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    /** 已经尝试过的次数（初次记录时为 1，每次 retry +1） */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 1;

    /** 最近一次失败原因摘要（截断 1024 字节，避免日志超大堆栈撑爆字段） */
    @Column(name = "last_fail_reason", length = 1024)
    private String lastFailReason;

    /** 下一次允许重试的时间 — retry job 用 {@code <= now} 过滤 */
    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    /** 重试成功后填充，便于人工排障 */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
