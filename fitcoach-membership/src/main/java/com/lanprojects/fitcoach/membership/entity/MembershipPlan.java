package com.lanprojects.fitcoach.membership.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会员套餐定义。
 *
 * <p><b>价格存储</b>：所有金额字段统一用最小货币单位（人民币用「分」，美元用「美分」），
 * 用 int 存储避免浮点精度问题（金钱永远不要用 double / float）。
 *
 * <p><b>iOS 价格</b>：因 Apple IAP 走苹果价格档位（Tier）销售，{@link #applePriceTier} 用于记录该套餐
 * 在 App Store Connect 配置的价格档位（1-87）。{@link #priceUsdCents} 仅作为业务侧"参考价"展示，
 * 实际 iOS 端用户看到的价格由苹果按其各市场汇率自动换算。
 *
 * <p><b>applePriceTier 和 applyProductId</b> 在 Apple IAP 接入前（无苹果开发者账号阶段）允许 NULL，
 * 接入时再 admin 后台填入。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_plan", indexes = {
        @Index(name = "uk_membership_plan_code", columnList = "plan_code", unique = true),
        @Index(name = "idx_membership_plan_enabled", columnList = "enabled")
})
public class MembershipPlan extends BaseEntity {

    /**
     * 套餐 code（业务 key），全大写下划线。例：DAILY / WEEKLY / MONTHLY / QUARTERLY / YEARLY。
     * 客户端通过 code 选套餐，server 据此查价、下单。一旦发布禁止改名。
     */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    /**
     * 显示名称（中文/可国际化）：日卡 / 周卡 / 月卡 / 季卡 / 年卡
     */
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /**
     * 有效期（天）。激活时算 expiresAt = activatedAt + durationDays（按 UTC 整数日累加）。
     */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /**
     * 国内价格（单位：分）。例：¥45.00 → 4500
     */
    @Column(name = "price_cny", nullable = false)
    private Integer priceCny;

    /**
     * iOS 端"参考"价格（单位：美分）。仅展示用，实际收款金额以苹果按 priceTier 换算的本地货币为准。
     * <p>未接入 IAP 阶段允许 NULL。
     */
    @Column(name = "price_usd_cents")
    private Integer priceUsdCents;

    /**
     * Apple 价格档位（1-87，参考 App Store Connect 价格表）。未接入 IAP 阶段为 NULL。
     */
    @Column(name = "apple_price_tier")
    private Integer applePriceTier;

    /**
     * Apple Product ID（在 App Store Connect 配置的 in-app purchase id），例：com.fitcoach.monthly。
     * 未接入 IAP 阶段允许 NULL。一旦发布禁止改名（会破坏已购买用户的恢复链路）。
     */
    @Column(name = "apple_product_id", length = 128)
    private String appleProductId;

    /**
     * Google Play Product ID（预留，海外 Android 走 Google Play Billing 时使用）
     */
    @Column(name = "google_product_id", length = 128)
    private String googleProductId;

    /**
     * 套餐说明（卡片副标题用，例："7 天无限制使用所有付费动作"）
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 排序权重（小 → 前）
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 是否启用：false = 停售（已购用户的会员仍有效，仅不再展示给新用户）。
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
