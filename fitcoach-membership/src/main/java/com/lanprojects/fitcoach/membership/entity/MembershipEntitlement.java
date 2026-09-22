package com.lanprojects.fitcoach.membership.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 一笔订单/赠送贡献的权益；退款按此记录撤销，不撤销整个账号。 */
@Entity
@Getter
@Setter
@Table(name = "membership_entitlement", indexes = {
        @Index(name = "uk_membership_entitlement_order", columnList = "order_id", unique = true),
        @Index(name = "idx_membership_entitlement_user", columnList = "user_id")})
public class MembershipEntitlement extends BaseEntity {
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "plan_id")
    private Long planId;
    @Column(name = "plan_code", length = 32)
    private String planCode;
    @Column(name = "duration_days", nullable = false)
    private int durationDays;
    @Column(name = "granted_at")
    private LocalDateTime grantedAt;
    @Column(name = "revoked", nullable = false)
    private boolean revoked;
    @Column(name = "baseline_expires_at")
    private LocalDateTime baselineExpiresAt;
}
