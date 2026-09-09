package com.lanprojects.fitcoach.payment.entity;

/**
 * 退款模式 —— 区分两种截然不同的退款发起机制。不同支付渠道的退款方式根本不同，
 * 必须用不同模式处理，这是退款子系统可跨渠道复用的架构基石。
 *
 * <ul>
 *   <li>{@link #ACTIVE} 主动退款：<b>商户调用通道退款 API</b>把钱原路退回，退款结果由通道同步/异步返回。
 *       适用：微信支付、支付宝、Google Play refund API、Stripe。</li>
 *   <li>{@link #PASSIVE} 被动退款：<b>用户直接向平台申请</b>，平台审核后通过服务器通知告知商户
 *       （Apple App Store Server Notifications V2 的 REFUND / Google RTDN / Voided Purchases），
 *       商户据此撤销权益并记账，<b>无法主动发起</b>。也用于「线下人工退款仅记账」场景。
 *       适用：Apple IAP、线下财务退款。</li>
 * </ul>
 */
public enum RefundMode {

    /** 主动退款：商户调通道 API 原路退回（微信 / 支付宝 / Google Play / Stripe） */
    ACTIVE,

    /** 被动退款：平台通知 / 线下人工，仅记账 + 撤权益（Apple IAP / 线下财务） */
    PASSIVE
}
