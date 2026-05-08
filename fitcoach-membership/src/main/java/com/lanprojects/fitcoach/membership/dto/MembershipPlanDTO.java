package com.lanprojects.fitcoach.membership.dto;

import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import lombok.Builder;
import lombok.Data;

/**
 * 会员套餐 DTO（客户端列表展示用）。
 *
 * <p>字段裁剪原则：
 * <ul>
 *   <li>不暴露内部 id（客户端按 planCode 选套餐下单）；</li>
 *   <li>iOS 关心 priceUsdCents / appleProductId；Android 关心 priceCny；都返回，由客户端按 platform 选；</li>
 *   <li>不暴露 applePriceTier（运营内部使用，客户端无需感知）；</li>
 *   <li>不暴露 enabled（这个接口本来只返启用的）。</li>
 * </ul>
 */
@Data
@Builder
public class MembershipPlanDTO {

    /** 套餐 code，例：MONTHLY */
    private String planCode;

    private String displayName;

    /** 有效期（天） */
    private Integer durationDays;

    /** 国内价格（分），客户端展示时除以 100 显示 */
    private Integer priceCny;

    /** iOS 端"参考"价格（美分，可空） */
    private Integer priceUsdCents;

    /** Apple Product ID（iOS 客户端用此 id 调 StoreKit purchase，可空） */
    private String appleProductId;

    /** Google Play Product ID（预留，可空） */
    private String googleProductId;

    /** 套餐说明，例："7 天无限制使用所有付费动作" */
    private String description;

    public static MembershipPlanDTO from(MembershipPlan p) {
        return MembershipPlanDTO.builder()
                .planCode(p.getPlanCode())
                .displayName(p.getDisplayName())
                .durationDays(p.getDurationDays())
                .priceCny(p.getPriceCny())
                .priceUsdCents(p.getPriceUsdCents())
                .appleProductId(p.getAppleProductId())
                .googleProductId(p.getGoogleProductId())
                .description(p.getDescription())
                .build();
    }
}
