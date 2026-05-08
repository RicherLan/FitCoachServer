package com.lanprojects.fitcoach.admin.dto.membership;

import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Admin 创建/更新套餐的入参。
 *
 * <p>**特殊语义**（更新场景）：
 * <ul>
 *   <li>对 priceUsdCents / applePriceTier 字段，{@code null} = 不动；{@code -1} = 清空（设为 NULL）；</li>
 *   <li>对 appleProductId / googleProductId 字段，{@code null} = 不动；空串 = 清空；</li>
 *   <li>这种约定让 PATCH 请求只传变更字段即可。</li>
 * </ul>
 */
@Data
public class AdminPlanRequest {

    /** 创建时必填，更新时忽略（service 禁止变更 planCode） */
    @NotBlank(groups = OnCreate.class)
    private String planCode;

    @NotBlank(groups = OnCreate.class)
    private String displayName;

    @NotNull(groups = OnCreate.class)
    @Positive(groups = OnCreate.class)
    private Integer durationDays;

    @NotNull(groups = OnCreate.class)
    @Positive(groups = OnCreate.class)
    private Integer priceCny;

    /** -1 清空，null 不动，>=0 设为该值 */
    private Integer priceUsdCents;

    /** -1 清空，null 不动，>=0 设为该值（1-87） */
    private Integer applePriceTier;

    /** 空串清空，null 不动 */
    private String appleProductId;

    private String googleProductId;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    /** 转 entity（创建时用） */
    public MembershipPlan toEntity() {
        MembershipPlan p = new MembershipPlan();
        p.setPlanCode(planCode);
        p.setDisplayName(displayName);
        p.setDurationDays(durationDays);
        p.setPriceCny(priceCny);
        p.setPriceUsdCents(priceUsdCents);
        p.setApplePriceTier(applePriceTier);
        p.setAppleProductId(appleProductId);
        p.setGoogleProductId(googleProductId);
        p.setDescription(description);
        if (sortOrder != null) p.setSortOrder(sortOrder);
        if (enabled != null) p.setEnabled(enabled);
        return p;
    }

    /** 转 entity（更新时用，按 service 的"null 即不动"语义） */
    public MembershipPlan toPatchEntity() {
        MembershipPlan p = new MembershipPlan();
        p.setDisplayName(displayName);
        p.setDurationDays(durationDays);
        p.setPriceCny(priceCny);
        p.setPriceUsdCents(priceUsdCents);
        p.setApplePriceTier(applePriceTier);
        p.setAppleProductId(appleProductId);
        p.setGoogleProductId(googleProductId);
        p.setDescription(description);
        p.setSortOrder(sortOrder);
        p.setEnabled(enabled);
        return p;
    }

    /** 校验分组：仅创建时校验必填字段；更新时全字段可选（patch 语义） */
    public interface OnCreate {}
}
