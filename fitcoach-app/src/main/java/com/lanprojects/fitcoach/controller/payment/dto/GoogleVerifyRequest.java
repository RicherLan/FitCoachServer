package com.lanprojects.fitcoach.controller.payment.dto;

import lombok.Data;

/**
 * Google Play 客户端验单请求（波 4）。
 *
 * @see com.lanprojects.fitcoach.payment.provider.google.GooglePlayService#verifyAndComplete
 */
@Data
public class GoogleVerifyRequest {

    /** 我方业务订单号（下单时 createOrder 返回的 orderId） */
    private String orderId;

    /** Google Play Billing 购买成功拿到的 purchaseToken */
    private String purchaseToken;

    /** Google Play 商品 id（productId） */
    private String productId;
}
