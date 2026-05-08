package com.lanprojects.fitcoach.admin.dto.membership;

import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端会员套餐 DTO（列表 / 详情通用）。
 *
 * <p>与客户端 MembershipPlanDTO 的差异：
 * <ul>
 *   <li>暴露 id（admin 编辑/删除用主键定位）；</li>
 *   <li>暴露 enabled / sortOrder（运营要看上下架状态、排序）；</li>
 *   <li>暴露 applePriceTier（运营内部要配 IAP 时填）；</li>
 *   <li>暴露 createdAt / updatedAt（审计）。</li>
 * </ul>
 */
@Data
@Builder
public class AdminPlanDto {

    private Long id;

    private String planCode;

    private String displayName;

    private Integer durationDays;

    private Integer priceCny;

    private Integer priceUsdCents;

    private Integer applePriceTier;

    private String appleProductId;

    private String googleProductId;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static AdminPlanDto from(MembershipPlan p) {
        return AdminPlanDto.builder()
                .id(p.getId())
                .planCode(p.getPlanCode())
                .displayName(p.getDisplayName())
                .durationDays(p.getDurationDays())
                .priceCny(p.getPriceCny())
                .priceUsdCents(p.getPriceUsdCents())
                .applePriceTier(p.getApplePriceTier())
                .appleProductId(p.getAppleProductId())
                .googleProductId(p.getGoogleProductId())
                .description(p.getDescription())
                .sortOrder(p.getSortOrder())
                .enabled(p.getEnabled())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
