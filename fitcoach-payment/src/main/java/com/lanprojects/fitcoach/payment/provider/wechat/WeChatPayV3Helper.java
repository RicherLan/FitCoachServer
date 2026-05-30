package com.lanprojects.fitcoach.payment.provider.wechat;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 微信支付 V3 协议工具类 — 签名构造、回调解密等。
 *
 * <p><b>签名算法</b>：RSA-SHA256 with RSA + Base64 编码。
 * <p><b>回调解密</b>：AES-256-GCM（API V3 Key 作为对称密钥）。
 *
 * <p>不依赖微信官方 SDK，仅使用 JDK 原生密码学 API，减少外部依赖。
 *
 * @see <a href="https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-generation.html">
 *     微信支付 V3 签名文档</a>
 */
@Slf4j
public final class WeChatPayV3Helper {

    private WeChatPayV3Helper() {}

    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final int GCM_TAG_LENGTH = 128; // bits

    // ====== 签名 ======

    /**
     * 构造微信支付 V3 Authorization Header。
     *
     * <pre>
     * WECHATPAY2-SHA256-RSA2048 mchid="xxx",nonce_str="xxx",timestamp="xxx",serial_no="xxx",signature="xxx"
     * </pre>
     *
     * @param method       HTTP 方法（GET / POST）
     * @param url          请求路径（不含域名，如 /v3/pay/transactions/app）
     * @param body         请求体 JSON（GET 请求传空字符串）
     * @param mchId        商户号
     * @param serialNo     商户证书序列号
     * @param privateKey   商户 API 私钥
     */
    public static String buildAuthorizationHeader(String method, String url, String body,
                                                   String mchId, String serialNo, PrivateKey privateKey) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = generateNonceStr();

        String message = method + "\n"
                + url + "\n"
                + timestamp + "\n"
                + nonceStr + "\n"
                + (body == null ? "" : body) + "\n";

        String signature = sign(message, privateKey);

        return String.format(
                "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",timestamp=\"%s\",serial_no=\"%s\",signature=\"%s\"",
                mchId, nonceStr, timestamp, serialNo, signature);
    }

    /**
     * RSA-SHA256 签名。
     */
    public static String sign(String message, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException("微信支付 V3 签名失败", e);
        }
    }

    /**
     * 构造 App 端拉起支付所需的二次签名 payload。
     *
     * <p>客户端（Android / iOS）调 WXApi.sendReq 时需要：
     * appId, partnerId (mchId), prepayId, nonceStr, timeStamp, sign, package="Sign=WXPay"。
     *
     * @param appId      微信开放平台 AppID
     * @param mchId      商户号（partnerId）
     * @param prepayId   统一下单返回的 prepay_id
     * @param privateKey 商户 API 私钥
     * @return 二次签名后的参数 map（可直接丢给客户端 SDK）
     */
    public static java.util.Map<String, Object> buildAppPayload(String appId, String mchId,
                                                                  String prepayId, PrivateKey privateKey) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = generateNonceStr();

        // 二次签名内容：appId + timestamp + nonceStr + prepayId（各行以 \n 结尾）
        String signContent = appId + "\n" + timestamp + "\n" + nonceStr + "\n" + prepayId + "\n";
        String paySign = sign(signContent, privateKey);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("appId", appId);
        payload.put("partnerId", mchId);
        payload.put("prepayId", prepayId);
        payload.put("nonceStr", nonceStr);
        payload.put("timeStamp", timestamp);
        payload.put("package", "Sign=WXPay");
        payload.put("sign", paySign);
        return payload;
    }

    // ====== 回调解密 ======

    /**
     * AES-256-GCM 解密微信支付回调通知的 resource 密文。
     *
     * <p>微信 V3 回调 body 结构：
     * <pre>
     * {
     *   "resource": {
     *     "algorithm": "AEAD_AES_256_GCM",
     *     "ciphertext": "...",
     *     "associated_data": "...",
     *     "nonce": "...",
     *     "original_type": "transaction"
     *   }
     * }
     * </pre>
     *
     * @param apiV3Key       API V3 密钥（32 字节）
     * @param nonce          resource.nonce
     * @param associatedData resource.associated_data
     * @param ciphertext     resource.ciphertext（Base64 编码）
     * @return 解密后的 JSON 字符串
     */
    public static String decryptAesGcm(String apiV3Key, String nonce,
                                        String associatedData, String ciphertext) {
        try {
            byte[] key = apiV3Key.getBytes(StandardCharsets.UTF_8);
            byte[] iv = nonce.getBytes(StandardCharsets.UTF_8);
            byte[] aad = associatedData != null
                    ? associatedData.getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(ciphertextBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("微信支付回调 AES-GCM 解密失败", e);
        }
    }

    /**
     * 校验微信回调签名（Wechatpay-Signature header）。
     *
     * <p>签名验证内容 = timestamp + "\n" + nonce + "\n" + body + "\n"，
     * 使用微信平台公钥 RSA-SHA256 校验。同时校验时间戳偏差（防重放，5 分钟窗口）。
     *
     * <p><b>平台证书获取</b>：商户首次接入时，需用商户私钥调微信 /v3/certificates 接口下载平台证书 PEM，
     * 配到 sysConfig 的 {@code payment.wechat.platformCertPem}。微信平台证书定期轮换（约 1 年），
     * 后续应实现自动拉取 + 缓存（当前 MVP 阶段手动配置即可）。
     *
     * @param timestamp         回调 header Wechatpay-Timestamp
     * @param nonce             回调 header Wechatpay-Nonce
     * @param body              回调 body 原文（不能预解析）
     * @param signature         回调 header Wechatpay-Signature (Base64)
     * @param platformPublicKey 微信平台证书提取出的公钥
     * @return 签名是否合法
     */
    public static boolean verifyCallbackSignature(String timestamp, String nonce, String body,
                                                   String signature, PublicKey platformPublicKey) {
        if (timestamp == null || nonce == null || body == null
                || signature == null || platformPublicKey == null) {
            log.error("[wechat-pay] 验签参数缺失 timestamp?={} nonce?={} body?={} sig?={} key?={}",
                    timestamp != null, nonce != null, body != null,
                    signature != null, platformPublicKey != null);
            return false;
        }

        // 校验时间戳防重放（允许 5 分钟偏差）
        try {
            long callbackTs = Long.parseLong(timestamp);
            long nowTs = System.currentTimeMillis() / 1000;
            if (Math.abs(nowTs - callbackTs) > 300) {
                log.error("[wechat-pay] 验签失败：时间戳偏差超过 5 分钟 callbackTs={} nowTs={}",
                        callbackTs, nowTs);
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("[wechat-pay] 验签失败：时间戳非法 timestamp={}", timestamp);
            return false;
        }

        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        try {
            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(platformPublicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            boolean ok = verifier.verify(Base64.getDecoder().decode(signature));
            if (!ok) {
                log.error("[wechat-pay] 验签失败：RSA 签名校验不通过");
            }
            return ok;
        } catch (Exception e) {
            log.error("[wechat-pay] 验签异常", e);
            return false;
        }
    }

    // ====== 私钥解析 ======

    /**
     * 将 PEM 格式的 PKCS8 私钥字符串解析为 {@link PrivateKey} 对象。
     *
     * <p>支持带/不带 "-----BEGIN PRIVATE KEY-----" 标记的格式。
     *
     * @param pemString PEM 私钥字符串
     */
    public static PrivateKey loadPrivateKeyFromPem(String pemString) {
        try {
            String key = pemString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载微信支付商户私钥失败（PEM 格式解析错误）", e);
        }
    }

    /**
     * 从 PEM 格式的 X.509 证书中提取公钥。微信平台证书可通过商户证书调
     * /v3/certificates 接口下载，返回的 ciphertext 经 AES-GCM 解密后即为 PEM 字符串。
     *
     * @param certPem 证书 PEM 字符串（带或不带 "-----BEGIN CERTIFICATE-----" 标记均可）
     */
    public static PublicKey loadPublicKeyFromCertPem(String certPem) {
        try {
            String normalized = certPem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            byte[] certBytes = Base64.getDecoder().decode(normalized);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(certBytes));
            return cert.getPublicKey();
        } catch (Exception e) {
            throw new RuntimeException("加载微信平台证书失败（PEM 格式解析错误）", e);
        }
    }

    // ====== 内部 ======

    public static String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
