package com.lanprojects.fitcoach.admin.dto.payment;

import com.lanprojects.fitcoach.common.client.AppFlavor;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端订单 DTO（含通道凭证 / IP / extraJson 等审计字段）。
 */
@Data
@Builder
public class AdminPaymentOrderDto {

    private Long id;
    private String orderId;
    private Long userId;
    private String userUid;        // 由 controller join 出来填上
    private String userNickname;   // 同上
    private String planCode;
    private String planSnapshotName;
    private PaymentChannel channel;
    private String clientPlatform;
    /** 下单时的 App Flavor（CN / GLOBAL / null=未标注），阶段 4 波 2 新增 */
    private AppFlavor appFlavor;
    private Integer amountCents;
    private String currency;
    private OrderStatus status;
    private RefundStatus refundStatus;
    private String channelPrepayId;
    private String channelTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private LocalDateTime refundedAt;
    private Integer refundAmountCents;
    private String failReason;

    public static AdminPaymentOrderDto from(PaymentOrder o) {
        return AdminPaymentOrderDto.builder()
                .id(o.getId())
                .orderId(o.getOrderId())
                .userId(o.getUserId())
                .planCode(o.getPlanCode())
                .planSnapshotName(o.getPlanSnapshotName())
                .channel(o.getChannel())
                .clientPlatform(o.getClientPlatform())
                .appFlavor(o.getAppFlavor())
                .amountCents(o.getAmountCents())
                .currency(o.getCurrency())
                .status(o.getStatus())
                .refundStatus(o.getRefundStatus())
                .channelPrepayId(o.getChannelPrepayId())
                .channelTransactionId(o.getChannelTransactionId())
                .createdAt(o.getCreatedAt())
                .paidAt(o.getPaidAt())
                .closedAt(o.getClosedAt())
                .refundedAt(o.getRefundedAt())
                .refundAmountCents(o.getRefundAmountCents())
                .failReason(o.getFailReason())
                .build();
    }
}
