package com.lanprojects.fitcoach.controller.payment.dto;

import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RN 客户端 → POST /api/payment/order 的入参。
 *
 * @see com.lanprojects.fitcoach.controller.payment.PaymentController
 */
@Data
public class CreateOrderRequest {

    /** 必填，套餐 code（在 /api/membership/plans 返回中可见） */
    @NotBlank
    private String planCode;

    /**
     * 可选，强制指定通道。不传时由 server 按平台路由（iOS→Apple, Android→WeChat, MOCK 模式优先 MOCK）。
     * <p>客户端一般不传，由服务端按平台决策。Admin 测试时可显式传 MOCK。
     */
    private PaymentChannel channel;
}
