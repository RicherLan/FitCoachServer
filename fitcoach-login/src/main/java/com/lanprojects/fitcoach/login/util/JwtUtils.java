package com.lanprojects.fitcoach.login.util;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * <p>
 * 支持 access / refresh 两种 token 类型（通过 claim "type" 区分），
 * 调用方在解析时必须显式校验类型，防止把 refresh token 当 access token 用。
 */
@Slf4j
public class JwtUtils {

    /** JWT claim：token 类型 —— "access" 或 "refresh" */
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private JwtUtils() {
    }

    /**
     * 生成 access token
     */
    public static String generateAccessToken(String uid, String secret, long expireMs) {
        return generate(uid, secret, expireMs, TYPE_ACCESS);
    }

    /**
     * 生成 refresh token
     */
    public static String generateRefreshToken(String uid, String secret, long expireMs) {
        return generate(uid, secret, expireMs, TYPE_REFRESH);
    }

    /**
     * 解析 token 并校验类型；任何失败都抛 BusinessException。
     *
     * @param expectedType 期望的 token 类型（access / refresh）
     * @return uid
     */
    public static String parseAndVerify(String token, String secret, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少访问凭证");
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TYPE, String.class);
            // 兼容老 token：未带 type claim 的视为 access
            if (type == null) {
                type = TYPE_ACCESS;
            }
            if (!expectedType.equals(type)) {
                log.warn("JWT token 类型不匹配，expected={}, actual={}", expectedType, type);
                throw new BusinessException(
                        TYPE_REFRESH.equals(expectedType) ? ResultCode.REFRESH_TOKEN_INVALID : ResultCode.TOKEN_INVALID);
            }
            String uid = claims.getSubject();
            if (uid == null || uid.isBlank()) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
            return uid;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token 已过期");
            throw new BusinessException(
                    TYPE_REFRESH.equals(expectedType) ? ResultCode.REFRESH_TOKEN_INVALID : ResultCode.TOKEN_EXPIRED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("JWT token 解析失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(
                    TYPE_REFRESH.equals(expectedType) ? ResultCode.REFRESH_TOKEN_INVALID : ResultCode.TOKEN_INVALID);
        }
    }

    // ====== 内部 ======

    private static String generate(String uid, String secret, long expireMs, String type) {
        if (secret == null || secret.length() < 32) {
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING, "JWT 密钥必须至少 32 字符");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        return Jwts.builder()
                .subject(uid)
                .claims(Map.of(CLAIM_TYPE, type))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
