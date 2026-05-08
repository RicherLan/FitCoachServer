package com.lanprojects.fitcoach.membership.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户会员状态（**只保存当前生效或最近一次的记录**，每个 user 只有一条）。
 *
 * <p><b>为什么 user_id 上加 unique 而不是为每次购买都建一行？</b>
 * <ul>
 *   <li>会员状态查询是高频操作（每次 RN 启动、每次进列表页都查），unique 索引让"按用户查会员"是 O(1)；</li>
 *   <li>购买历史/订单记录由 {@code PaymentOrder} 表承担（已带 user_id），不需要在这里冗余；</li>
 *   <li>续费时直接更新 expiresAt（叠加 durationDays），而不是新建行——保持当前会员状态唯一；</li>
 *   <li>历史会员状态可由 PaymentOrder 反推（哪天买的、买了什么），不丢失审计追溯能力。</li>
 * </ul>
 *
 * <p><b>时间存储</b>：所有时间字段用 {@link LocalDateTime}，与 {@link BaseEntity#getCreatedAt()} 保持一致，
 * 服务器统一按 UTC 写入（建议运行时设 {@code -Duser.timezone=UTC}），客户端按本地时区格式化展示。
 * <br/>之所以不用 Instant，是为了与全工程时间字段类型对齐，便于 Jackson 序列化和 JPA 查询参数传递。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_membership", indexes = {
        @Index(name = "uk_user_membership_user", columnList = "user_id", unique = true),
        @Index(name = "idx_user_membership_expires_at", columnList = "expires_at")
})
public class UserMembership extends BaseEntity {

    /**
     * 用户 id（unique，保证一个 user 只有一条记录）
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 当前生效的套餐 id
     */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /**
     * 套餐 code 冗余（用于查询时不用 join 表，加速 RN /api/membership/my-status 接口）
     */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    /**
     * 当前会员激活时间（首次购买的时间；续费时不更新此字段，仍为首次激活）
     */
    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    /**
     * 当前会员到期时间（UTC）。**业务判定"是否会员"的核心字段**：now < expiresAt 即视为有效。
     * <p>续费时 = max(now, currentExpiresAt) + durationDays（避免提前续费亏天数）。
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 是否启用自动续订（MVP 始终为 false，预留字段，未来对接苹果/微信自动续订订阅时使用）。
     */
    @Column(name = "auto_renew_enabled", nullable = false)
    private Boolean autoRenewEnabled = false;

    /**
     * 最近一次激活/续费的订单 id（业务订单号，便于审计追溯"这次到期是哪次订单延上来的"）
     */
    @Column(name = "last_order_id", length = 64)
    private String lastOrderId;

    /**
     * 是否当前会员（业务方便方法，不是数据库字段）。
     */
    public boolean isActive() {
        return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
