package com.lanprojects.fitcoach.admin.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Admin 标记退款的入参。
 *
 * @see com.lanprojects.fitcoach.payment.service.PaymentService#adminMarkRefunded
 */
@Data
public class AdminRefundRequest {

    /** 退款金额（最小货币单位）；不传或 <=0 时按全额退 */
    private Integer refundCents;

    /** 退款原因（必填，落 fail_reason 用于审计） */
    @NotBlank
    private String reason;
}
