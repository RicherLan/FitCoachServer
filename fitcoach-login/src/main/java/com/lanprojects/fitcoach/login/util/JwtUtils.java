package com.lanprojects.fitcoach.login.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
public class JwtUtils {

    private JwtUtils() {
    }

    /**
     * 生成 JWT token
     *
     * @param uid       用户唯一标识
     * @param secret    签名密钥（至少 32 字符）
     * @param expireMs  过期时间（毫秒）
     * @return JWT token 字符串
     */
    public static String generateToken(String uid, String secret, long expireMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        return Jwts.builder()
                .subject(uid)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，获取用户 uid
     *
     * @return uid，解析失败返回 null
     */
    public static String parseToken(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token 已过期: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("JWT token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查 token 是否有效（未过期且签名正确）
     */
    public static boolean isValid(String token, String secret) {
        return parseToken(token, secret) != null;
    }
}
