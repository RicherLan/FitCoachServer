package com.lanprojects.fitcoach.payment.provider.wechat;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 同一信任源用于API响应与通知验签；按微信返回的公钥ID/证书序列号精确匹配。 */
@Component
@RequiredArgsConstructor
public class WeChatSignatureVerifier {
    private final SysConfigService config;

    public String preferredSerial() {
        String id = config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_ID);
        String pem = config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_PEM);
        if (present(id) || present(pem)) {
            if (!present(id) || !id.startsWith("PUB_KEY_ID_") || !present(pem)) {
                throw new IllegalArgumentException("微信支付公钥ID与PEM必须成对配置");
            }
            publicKey(pem);
            return id;
        }
        return certificate().getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT);
    }
    public boolean isConfigured() {
        try { preferredSerial(); return true; } catch (Exception e) { return false; }
    }
    public boolean verify(String timestamp, String nonce, String body, String signature, String serial) {
        if (!present(serial)) return false;
        try {
            PublicKey key;
            if (serial.startsWith("PUB_KEY_ID_")) {
                if (!serial.equals(config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_ID))) return false;
                key = publicKey(config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_PEM));
            } else {
                var cert = certificate();
                if (!cert.getSerialNumber().toString(16).equalsIgnoreCase(serial)) return false;
                key = cert.getPublicKey();
            }
            return WeChatPayV3Helper.verifyCallbackSignature(timestamp, nonce, body, signature, key);
        } catch (Exception e) { return false; }
    }
    private PublicKey publicKey(String pem) {
        try {
            String raw = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s+", "");
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(raw)));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("微信支付公钥应为RSA2048或更强");
            }
            return key;
        } catch (Exception e) { throw new IllegalArgumentException("微信支付公钥PEM无效", e); }
    }
    private X509Certificate certificate() {
        try {
            String pem = config.getValue(PaymentConfigKeys.WECHAT_PLATFORM_CERT_PEM);
            if (!present(pem)) throw new IllegalArgumentException("缺少微信支付验签材料");
            var cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            cert.checkValidity();
            return cert;
        } catch (Exception e) { throw new IllegalArgumentException("微信平台证书无效或已过期", e); }
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
