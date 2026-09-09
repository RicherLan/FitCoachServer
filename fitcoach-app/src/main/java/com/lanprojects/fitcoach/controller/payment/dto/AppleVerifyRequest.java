package com.lanprojects.fitcoach.controller.payment.dto;

import lombok.Data;

/**
 * Apple IAP 客户端验单请求（波 3）。
 *
 * @see com.lanprojects.fitcoach.payment.provider.apple.AppleIapService#verifyAndComplete
 */
@Data
public class AppleVerifyRequest {

    /** 我方业务订单号（下单时 createOrder 返回的 orderId） */
    private String orderId;

    /** 客户端 StoreKit 2 购买成功拿到的 signedTransaction（JWS） */
    private String signedTransaction;
}
