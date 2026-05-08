package com.lanprojects.fitcoach.membership.dto;

import com.lanprojects.fitcoach.membership.entity.UserMembership;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户当前会员状态（客户端首页 / 设置页 / 训练前 readiness 校验都会用）。
 *
 * <p>**关键字段**：
 * <ul>
 *   <li>{@link #isActive}：客户端唯一的可信判定字段（true = 当前是会员）；</li>
 *   <li>{@link #planCode} / {@link #planDisplayName}：当前套餐的展示，便于"会员到 2025-12-31"这种文案；</li>
 *   <li>{@link #expiresAt} / {@link #activatedAt}：精确到秒的 ISO 时间，由客户端按本地时区格式化。</li>
 * </ul>
 *
 * <p>当用户从未开通过会员时，由 Controller 直接构造一个 {@code isActive=false, planCode=null} 的实例。
 */
@Data
@Builder
public class MembershipStatusDTO {

    /** 是否当前生效（now < expiresAt）。客户端只信这个字段。 */
    private Boolean isActive;

    /** 当前套餐 code（无会员时为 null） */
    private String planCode;

    /** 当前套餐显示名（无会员时为 null） */
    private String planDisplayName;

    /** 激活时间（首次开通时间，续费不变）。无会员时为 null。 */
    private LocalDateTime activatedAt;

    /** 到期时间。无会员时为 null。 */
    private LocalDateTime expiresAt;

    /** 是否启用自动续订（MVP 始终 false） */
    private Boolean autoRenewEnabled;

    /**
     * 构造"非会员"状态（用于 user_membership 表查不到记录的情况）。
     */
    public static MembershipStatusDTO nonMember() {
        return MembershipStatusDTO.builder()
                .isActive(false)
                .autoRenewEnabled(false)
                .build();
    }

    public static MembershipStatusDTO from(UserMembership m, String planDisplayName) {
        return MembershipStatusDTO.builder()
                .isActive(m.isActive())
                .planCode(m.getPlanCode())
                .planDisplayName(planDisplayName)
                .activatedAt(m.getActivatedAt())
                .expiresAt(m.getExpiresAt())
                .autoRenewEnabled(Boolean.TRUE.equals(m.getAutoRenewEnabled()))
                .build();
    }
}
