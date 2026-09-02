package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.dto.GoogleIdTokenPayload;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Google idToken 的 JWK 校验器（阶段 3B 波 2）。
 *
 * <p><b>校验流程</b>（遵循 Google 官方规范
 * <a href="https://developers.google.com/identity/gsi/web/guides/verify-google-id-token">Verify the Google ID token</a>）：
 * <ol>
 *   <li>从 {@code https://www.googleapis.com/oauth2/v3/certs} 拉取 Google 公钥集合（RemoteJWKSet 自带缓存）；</li>
 *   <li>用 kid 从 JWK 集合选择对应公钥，验证 idToken 的 RS256 签名；</li>
 *   <li>校验 iss（issuer）∈ {@link #ALLOWED_ISSUERS}（Google 官方接受 https://accounts.google.com / accounts.google.com）；</li>
 *   <li>校验 aud（audience）在允许的 Client ID 列表内；</li>
 *   <li>校验 exp（过期时间）未过；</li>
 *   <li>提取 sub / email / email_verified / name / picture 组成 {@link GoogleIdTokenPayload} 返回。</li>
 * </ol>
 *
 * <p><b>与 {@code AppleTokenVerifier} 的差异</b>：
 * <ul>
 *   <li>Google iss 有两个合法值（带/不带协议前缀），因此比对用 {@link #ALLOWED_ISSUERS} 集合；</li>
 *   <li>Google 载荷字段更丰富（name / picture），验签逻辑本身与 Apple 完全一致（都是 RS256 + JWKS）。</li>
 * </ul>
 *
 * <p><b>线程安全</b>：{@link JWKSource} + {@link ConfigurableJWTProcessor} 均线程安全，
 * processor 由双重检查锁一次性初始化后全局复用。
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    /** Google 公钥集合 URL，全球固定，不需要走 SysConfig。 */
    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    /**
     * Google 官方允许的两个 issuer；两者语义等价，历史原因导致同时存在。
     * 参考 <a href="https://developers.google.com/identity/gsi/web/guides/verify-google-id-token#verify_the_id_token_manually">manual verification</a>。
     */
    private static final Set<String> ALLOWED_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    /**
     * 校验并解析 idToken。
     *
     * @param idToken Google SDK 返回的 idToken 字符串
     * @param allowedAudiences 允许的 audience 集合（一般是 iOS / Android / Web 三个 OAuth Client ID）
     * @return 校验通过后的载荷；不通过直接抛 {@link BusinessException}
     */
    public GoogleIdTokenPayload verify(String idToken, Set<String> allowedAudiences) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID, "idToken 为空");
        }
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            log.error("[google] 未配置任何允许的 audience，拒绝所有 Google 登录请求");
            throw new BusinessException(ResultCode.GOOGLE_CONFIG_MISSING, "Google audience 未配置");
        }

        ConfigurableJWTProcessor<SecurityContext> processor = getOrCreateProcessor();

        JWTClaimsSet claims;
        try {
            claims = processor.process(idToken, null);
        } catch (Exception e) {
            log.warn("[google] idToken 校验失败: type={}, msg={}, tokenMask={}",
                    e.getClass().getSimpleName(), e.getMessage(), LogUtils.mask(idToken));
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID,
                    "Google idToken 校验失败：" + e.getMessage());
        }

        String issuer = claims.getIssuer();
        if (issuer == null || !ALLOWED_ISSUERS.contains(issuer)) {
            log.warn("[google] iss 校验失败: expected={}, actual={}", ALLOWED_ISSUERS, issuer);
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID,
                    "Google idToken 的 iss 不合法");
        }

        String tokenAudience = extractAudience(claims);
        if (tokenAudience == null || !allowedAudiences.contains(tokenAudience)) {
            log.warn("[google] aud 校验失败: tokenAud={}, allowed={}", tokenAudience, allowedAudiences);
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID,
                    "Google idToken 的 aud 未在允许列表内");
        }

        Date exp = claims.getExpirationTime();
        if (exp != null && exp.before(new Date())) {
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID, "Google idToken 已过期");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BusinessException(ResultCode.GOOGLE_TOKEN_INVALID, "Google idToken 缺少 sub");
        }

        String email = safeStringClaim(claims, "email");
        Boolean emailVerified = coerceBoolean(claims.getClaim("email_verified"));
        String name = safeStringClaim(claims, "name");
        String picture = safeStringClaim(claims, "picture");

        log.info("[google] idToken 校验通过, subMask={}, aud={}, hasEmail={}, hasName={}",
                LogUtils.mask(sub), tokenAudience, email != null, name != null);

        return new GoogleIdTokenPayload(sub, email, emailVerified, name, picture, tokenAudience);
    }

    // ====== 内部 ======

    /** processor 一次初始化终身复用，双重检查锁避免并发初始化拉两次 JWKS */
    private volatile ConfigurableJWTProcessor<SecurityContext> cachedProcessor;

    private ConfigurableJWTProcessor<SecurityContext> getOrCreateProcessor() {
        ConfigurableJWTProcessor<SecurityContext> p = cachedProcessor;
        if (p == null) {
            synchronized (this) {
                p = cachedProcessor;
                if (p == null) {
                    p = buildProcessor();
                    cachedProcessor = p;
                }
            }
        }
        return p;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor() {
        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .create(new URL(GOOGLE_JWKS_URL))
                    .retrying(true)
                    .refreshAheadCache(true)
                    .build();

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            processor.setJWSKeySelector(keySelector);
            log.info("[google] JWT processor 初始化完成, jwksUrl={}", GOOGLE_JWKS_URL);
            return processor;
        } catch (Exception e) {
            log.error("[google] JWT processor 初始化失败", e);
            throw new BusinessException(ResultCode.GOOGLE_CONFIG_MISSING,
                    "Google JWT processor 初始化失败：" + e.getMessage());
        }
    }

    private String extractAudience(JWTClaimsSet claims) {
        List<String> audList = claims.getAudience();
        if (audList == null || audList.isEmpty()) {
            return null;
        }
        return audList.get(0);
    }

    private String safeStringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Google 有时把 email_verified 写成 String，有时写成 boolean。做鲁棒转换。
     */
    private Boolean coerceBoolean(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return null;
    }
}
