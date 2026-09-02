package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.dto.AppleIdTokenPayload;
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
import java.util.Set;

/**
 * Apple identityToken 的 JWK 校验器（阶段 3B 波 1）。
 *
 * <p><b>校验流程</b>（严格按 Apple 官方规范）：
 * <ol>
 *   <li>从 <code>https://appleid.apple.com/auth/keys</code> 拉取 Apple 公钥集合（RemoteJWKSet 自带 5min 缓存 + 30min 兜底）；</li>
 *   <li>用 kid 从 JWK 集合选择对应公钥，验证 identityToken 的 RS256 签名；</li>
 *   <li>校验 iss（issuer）== <code>https://appleid.apple.com</code>；</li>
 *   <li>校验 aud（audience）在允许的 audience 列表内（<b>iOS Bundle ID / macOS bundle / Web Services ID</b>）；</li>
 *   <li>校验 exp（过期时间）未过；</li>
 *   <li>提取 sub / email / email_verified / is_private_email / aud 组成 {@link AppleIdTokenPayload} 返回。</li>
 * </ol>
 *
 * <p><b>为什么单独抽 verifier 而不并入 AppleService</b>：JWK 校验是纯签名 / claim 判断逻辑，
 * 不涉及数据库 / SysConfig / User 实体；AppleService 需要读取配置 + 编排流程。
 * 分层后 verifier 可单独单测，无需 mock 全套 Spring 上下文。
 *
 * <p><b>线程安全</b>：{@link JWKSource} + {@link ConfigurableJWTProcessor} 均是线程安全，本类持有的 processor
 * 由 {@link AppleService#init()} 一次性初始化，之后所有校验共用同一实例。
 *
 * <p><b>Apple identityToken 参考</b>：
 * <a href="https://developer.apple.com/documentation/sign_in_with_apple/verifying_a_user">Verifying a User</a>
 */
@Slf4j
@Component
public class AppleTokenVerifier {

    /** Apple 公钥集合 URL，全球固定，不需要走 SysConfig。 */
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    /** Apple identityToken 的 iss（发行方），Apple 全球固定。 */
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    /** 拉取超时 3s；正常 Apple 响应 <500ms，超过 3s 通常代表网络异常。 */
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    /** 读取超时 3s，与 connect 一致。 */
    private static final int READ_TIMEOUT_MS = 3_000;

    /**
     * 校验并解析 identityToken。
     *
     * @param identityToken Apple SDK 返回的 identityToken 字符串
     * @param allowedAudiences 允许的 audience 集合（iOS Bundle ID / Services ID 等）
     * @return 校验通过后的载荷；不通过直接抛 {@link BusinessException}
     */
    public AppleIdTokenPayload verify(String identityToken, Set<String> allowedAudiences) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID, "identityToken 为空");
        }
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            log.error("[apple] 未配置任何允许的 audience，拒绝所有 Apple 登录请求");
            throw new BusinessException(ResultCode.APPLE_CONFIG_MISSING, "Apple audience 未配置");
        }

        ConfigurableJWTProcessor<SecurityContext> processor = getOrCreateProcessor();

        JWTClaimsSet claims;
        try {
            claims = processor.process(identityToken, null);
        } catch (Exception e) {
            // 签名不合法 / kid 找不到 / JWKS 拉取失败 / JSON 解析失败 均归到"token 无效"
            log.warn("[apple] identityToken 校验失败: type={}, msg={}, tokenMask={}",
                    e.getClass().getSimpleName(), e.getMessage(), LogUtils.mask(identityToken));
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID,
                    "Apple identityToken 校验失败：" + e.getMessage());
        }

        // 校验 iss
        String issuer = claims.getIssuer();
        if (!APPLE_ISSUER.equals(issuer)) {
            log.warn("[apple] iss 校验失败: expected={}, actual={}", APPLE_ISSUER, issuer);
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID,
                    "Apple identityToken 的 iss 不合法");
        }

        // 校验 aud —— 必须与允许集合中的任一项匹配
        // Apple identityToken 的 aud 通常是单值 String（对应 App 的 Bundle ID / Services ID）
        String tokenAudience = extractAudience(claims);
        if (tokenAudience == null || !allowedAudiences.contains(tokenAudience)) {
            log.warn("[apple] aud 校验失败: tokenAud={}, allowed={}", tokenAudience, allowedAudiences);
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID,
                    "Apple identityToken 的 aud 未在允许列表内");
        }

        // 校验 exp（Nimbus 的 default processor 已内置 exp 校验，此处属于兜底）
        Date exp = claims.getExpirationTime();
        if (exp != null && exp.before(new Date())) {
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID, "Apple identityToken 已过期");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BusinessException(ResultCode.APPLE_TOKEN_INVALID, "Apple identityToken 缺少 sub");
        }

        String email = safeStringClaim(claims, "email");
        Boolean emailVerified = coerceBoolean(claims.getClaim("email_verified"));
        Boolean isPrivateEmail = coerceBoolean(claims.getClaim("is_private_email"));

        log.info("[apple] identityToken 校验通过, subMask={}, aud={}, hasEmail={}, isPrivate={}",
                LogUtils.mask(sub), tokenAudience, email != null, isPrivateEmail);

        return new AppleIdTokenPayload(sub, email, emailVerified, isPrivateEmail, tokenAudience);
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
                    .create(new URL(APPLE_JWKS_URL))
                    // Nimbus 默认 5 分钟拉取缓存 + 30 秒失败重试；显式设置为便于将来调整。
                    .retrying(true)
                    .refreshAheadCache(true)
                    .build();

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            processor.setJWSKeySelector(keySelector);
            log.info("[apple] JWT processor 初始化完成, jwksUrl={}", APPLE_JWKS_URL);
            return processor;
        } catch (Exception e) {
            log.error("[apple] JWT processor 初始化失败", e);
            throw new BusinessException(ResultCode.APPLE_CONFIG_MISSING,
                    "Apple JWT processor 初始化失败：" + e.getMessage());
        }
    }

    /**
     * 从 aud claim 提取字符串。
     * <p>Apple identityToken 的 aud 通常是单值 String，Nimbus 内部会解析成 {@code List<String>}；
     * 兼容 List / 单值两种形式，取第一个。
     */
    private String extractAudience(JWTClaimsSet claims) {
        java.util.List<String> audList = claims.getAudience();
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
     * Apple 有时把 email_verified / is_private_email 写成 String "true"/"false"，
     * 有时写成 boolean。这里做鲁棒转换，避免直接 getBooleanClaim 时 ClassCastException。
     */
    private Boolean coerceBoolean(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return null;
    }
}
