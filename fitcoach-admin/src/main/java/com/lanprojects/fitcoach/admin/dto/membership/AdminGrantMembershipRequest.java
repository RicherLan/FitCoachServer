package com.lanprojects.fitcoach.admin.dto.membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Admin 手动赠送会员的入参。
 *
 * <p>支持两种"挂单"方式：
 * <ul>
 *   <li>{@link #planCode}：必填。指定挂在哪个套餐 code 下（用于统计 / user 看到自己是哪种会员）；</li>
 *   <li>{@link #days}：必填正整数。实际增加多少天会员（与 plan.durationDays 解耦，运营可灵活赠送）；</li>
 *   <li>{@link #reason}：可选，落入 {@code lastOrderId} 字段的 "GIFT_xxx" 后缀，便于审计。</li>
 * </ul>
 */
@Data
public class AdminGrantMembershipRequest {

    /** 挂在哪个套餐 code 下（必须存在的 code，停售也行） */
    @NotBlank
    private String planCode;

    /** 赠送多少天 */
    @NotNull
    @Positive
    private Integer days;

    /** 操作原因（落入 lastOrderId 用于审计追溯，可空） */
    private String reason;
}
