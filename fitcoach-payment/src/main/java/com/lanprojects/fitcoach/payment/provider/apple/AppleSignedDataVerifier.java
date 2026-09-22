package com.lanprojects.fitcoach.payment.provider.apple;

import com.apple.itunes.storekit.model.*;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.*;

/** 使用 Apple 官方库验证证书链、签名、环境和应用标识；根证书来自受信任配置。 */
@Component
@RequiredArgsConstructor
public class AppleSignedDataVerifier {
    private final SysConfigService config;

    private SignedDataVerifier verifier() {
        try {
            String bundle = config.getValue(PaymentConfigKeys.APPLE_BUNDLE_ID);
            String roots = config.getValue(PaymentConfigKeys.APPLE_ROOT_CERTIFICATES);
            if (bundle == null || bundle.isBlank() || roots == null || roots.isBlank()) {
                throw new IllegalArgumentException("missing Apple verification configuration");
            }
            boolean sandbox = config.getBoolValue(PaymentConfigKeys.APPLE_SANDBOX, false);
            String appId = config.getValue(PaymentConfigKeys.APPLE_APP_ID);
            Set<InputStream> certificates = new HashSet<>();
            for (var certificate : CertificateFactory.getInstance("X.509").generateCertificates(
                    new ByteArrayInputStream(roots.getBytes(StandardCharsets.UTF_8)))) {
                certificates.add(new ByteArrayInputStream(certificate.getEncoded()));
            }
            if (certificates.isEmpty()) { throw new IllegalArgumentException("empty roots"); }
            return new SignedDataVerifier(certificates, bundle,
                    appId == null || appId.isBlank() ? null : Long.valueOf(appId),
                    sandbox ? Environment.SANDBOX : Environment.PRODUCTION, true);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING, "Apple 验签配置不完整或无效");
        }
    }

    public JWSTransactionDecodedPayload transaction(String jws) {
        var verifier = verifier();
        try { return verifier.verifyAndDecodeTransaction(jws); }
        catch (Exception e) { throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID); }
    }
    public ResponseBodyV2DecodedPayload notification(String jws) {
        var verifier = verifier();
        try { return verifier.verifyAndDecodeNotification(jws); }
        catch (Exception e) { throw new BusinessException(ResultCode.PAYMENT_RECEIPT_INVALID); }
    }
    public static UUID accountToken(String orderId) {
        return UUID.nameUUIDFromBytes(("fitcoach:apple:" + orderId).getBytes(StandardCharsets.UTF_8));
    }
}
