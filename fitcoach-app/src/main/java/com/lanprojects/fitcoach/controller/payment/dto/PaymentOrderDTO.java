package com.lanprojects.fitcoach.controller.payment.dto;

import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端订单 DTO（个人订单详情 / 列表项共用）。
 *
 * <p>不暴露 channelTransactionId / channelPrepayId / extraJson 等通道侧字段，避免给客户端可滥用的信息面。
 */
@Data
@Builder
public class PaymentOrderDTO {

    private String orderId;

    private String planCode;

    private String planSnapshotName;

    private PaymentChannel channel;

    private Integer amountCents;

    private String currency;

    private OrderStatus status;

    private RefundStatus refundStatus;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    private LocalDateTime refundedAt;

    private Integer refundAmountCents;

    private String failReason;

    public static PaymentOrderDTO from(PaymentOrder o) {
        return PaymentOrderDTO.builder()
                .orderId(o.getOrderId())
                .planCode(o.getPlanCode())
                .planSnapshotName(o.getPlanSnapshotName())
                .channel(o.getChannel())
                .amountCents(o.getAmountCents())
                .currency(o.getCurrency())
                .status(o.getStatus())
                .refundStatus(o.getRefundStatus())
                .createdAt(o.getCreatedAt())
                .paidAt(o.getPaidAt())
                .closedAt(o.getClosedAt())
                .refundedAt(o.getRefundedAt())
                .refundAmountCents(o.getRefundAmountCents())
                .failReason(o.getFailReason())
                .build();
    }
}
