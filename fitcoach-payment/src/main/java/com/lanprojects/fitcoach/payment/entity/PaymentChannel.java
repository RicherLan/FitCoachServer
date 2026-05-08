package com.lanprojects.fitcoach.payment.entity;

/**
 * 支付通道枚举。
 *
 * <p>设计考虑：
 * <ul>
 *   <li>用 enum 而非字符串：保证拼写安全 + 入库可读；</li>
 *   <li>{@code MOCK} 通道仅在开发/测试启用，由 {@code payment.mock.enabled=true} 配置控制；</li>
 *   <li>{@code APPLE_IAP} / {@code GOOGLE_PLAY} 在 IAP 接入完成前由占位 Provider 返回"暂不支持"。</li>
 * </ul>
 */
public enum PaymentChannel {
    /** 微信支付（国内 Android 主通道） */
    WECHAT,

    /** 支付宝（预留，未来接入） */
    ALIPAY,

    /** Apple In-App Purchase（iOS 必走） */
    APPLE_IAP,

    /** Google Play Billing（海外 Android，预留） */
    GOOGLE_PLAY,

    /** 测试桩通道（开发期模拟支付成功） */
    MOCK
}
