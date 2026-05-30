package com.lanprojects.fitcoach.payment.provider;

/**
 * 支付模块所有 SysConfig 配置项 key 的集中常量。集中放一处的好处：
 * <ul>
 *   <li>避免散落字符串导致拼写错误；</li>
 *   <li>admin 后台展示配置时可以照着这个表配；</li>
 *   <li>未来重命名一处全改。</li>
 * </ul>
 */
public final class PaymentConfigKeys {

    private PaymentConfigKeys() {}

    // ====== 全局开关 ======

    /** Mock 支付通道是否启用（开发期 true，生产严禁开启） */
    public static final String MOCK_ENABLED = "payment.mock.enabled";

    // ====== 微信支付 ======

    /** 微信支付总开关 */
    public static final String WECHAT_ENABLED = "payment.wechat.enabled";
    /** 微信开放平台 AppID（不是公众号那个） */
    public static final String WECHAT_APP_ID = "payment.wechat.appId";
    /** 微信支付商户号 */
    public static final String WECHAT_MCH_ID = "payment.wechat.mchId";
    /** 微信支付 API V3 密钥（加密存储） */
    public static final String WECHAT_API_V3_KEY = "payment.wechat.apiV3Key";
    /** 商户私钥序列号（V3 用） */
    public static final String WECHAT_MCH_SERIAL_NO = "payment.wechat.mchSerialNo";
    /** 微信支付回调 URL（外部可访问域名） */
    public static final String WECHAT_NOTIFY_URL = "payment.wechat.notifyUrl";
    /** 商户 API 私钥（PEM 格式，加密存储） */
    public static final String WECHAT_MCH_PRIVATE_KEY = "payment.wechat.mchPrivateKey";
    /** 微信支付平台证书 PEM（用于回调验签，加密存储）。可通过商户证书调 /v3/certificates 接口获取 */
    public static final String WECHAT_PLATFORM_CERT_PEM = "payment.wechat.platformCertPem";
    /** 开发模式：跳过微信回调签名校验（默认 false，生产环境严禁开启） */
    public static final String WECHAT_SKIP_CALLBACK_SIGNATURE = "payment.wechat.skipCallbackSignature";

    // ====== Apple IAP ======

    /** Apple IAP 总开关 */
    public static final String APPLE_ENABLED = "payment.apple.enabled";
    /** Apple Bundle ID（如 com.lanprojects.fitcoach） */
    public static final String APPLE_BUNDLE_ID = "payment.apple.bundleId";
    /** Apple App-Specific Shared Secret（订阅必备，加密） */
    public static final String APPLE_SHARED_SECRET = "payment.apple.sharedSecret";
    /** 是否走 sandbox 环境（开发/审核时 true） */
    public static final String APPLE_SANDBOX = "payment.apple.sandbox";
}
