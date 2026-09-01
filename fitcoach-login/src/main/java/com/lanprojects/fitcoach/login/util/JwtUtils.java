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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 工具类
 * <p>
 * 支持 access / refresh 两种 token 类型（通过 claim "type" 区分），
 * 调用方在解析时必须显式校验类型，防止把 refresh token 当 access token 用。
 *
 * <p><b>单设备登录互踢（sid claim）</b>：
 * <ul>
 *   <li>登录成功时若 server 决定写 user.currentSessionId（由 AuthService 根据
 *       {@code ClientContext.get().hasDeviceId()} 判断），同步把 sessionId 写入 JWT 的
 *       {@code sid} claim；</li>
 *   <li>{@link AuthService#getCurrentUser(String)} 解析 token 后比对 jwt.sid 与
 *       user.currentSessionId，不一致即抛 {@link ResultCode#SESSION_KICKED}；</li>
 *   <li>无 sid claim 的老 token（升级前签发 / admin 等无 deviceId 客户端签发）→
 *       {@link JwtPayload#sessionId()} 返回 null，AuthService 会跳过 sid 校验（向后兼容）。</li>
 * </ul>
 */
@Slf4j
public class JwtUtils {

    /** JWT claim：token 类型 —— "access" 或 "refresh" */
    public static final String CLAIM_TYPE = "type";
    /** JWT claim：单设备登录互踢用的 session id（缺失即不参与互踢） */
    public static final String CLAIM_SID = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private JwtUtils() {
    }

    /**
     * 生成 access token（不带 sid，向后兼容）
     */
    public static String generateAccessToken(String uid, String secret, long expireMs) {
        return generate(uid, secret, expireMs, TYPE_ACCESS, null);
    }

    /**
     * 生成 refresh token（不带 sid，向后兼容）
     */
    public static String generateRefreshToken(String uid, String secret, long expireMs) {
        return generate(uid, secret, expireMs, TYPE_REFRESH, null);
    }

    /**
     * 生成 access token，并把 sessionId 写入 sid claim（用于单设备登录互踢）。
     * <p>{@code sessionId} 为 null/blank 时退化为不带 sid，与
     * {@link #generateAccessToken(String, String, long)} 等价。
     */
    public static String generateAccessToken(String uid, String secret, long expireMs, String sessionId) {
        return generate(uid, secret, expireMs, TYPE_ACCESS, sessionId);
    }

    /**
     * 生成 refresh token，并把 sessionId 写入 sid claim（用于单设备登录互踢）。
     */
    public static String generateRefreshToken(String uid, String secret, long expireMs, String sessionId) {
        return generate(uid, secret, expireMs, TYPE_REFRESH, sessionId);
    }

    /**
     * 解析 token 并校验类型；任何失败都抛 BusinessException。
     *
     * @param expectedType 期望的 token 类型（access / refresh）
     * @return uid（不需要 sid 的旧调用方可继续用此重载）
     */
    public static String parseAndVerify(String token, String secret, String expectedType) {
        return parsePayload(token, secret, expectedType).uid();
    }

    /**
     * 解析 token 并校验类型，返回 (uid, sessionId)。
     * <p>{@code sessionId} 在以下情况返回 null：
     * <ul>
     *   <li>升级前签发的老 token（无 sid claim）；</li>
     *   <li>admin 后台 / Postman 等无 deviceId 客户端签发的 token（AuthService 决定不写 sid）。</li>
     * </ul>
     * 调用方（AuthService）应据此判断是否参与单设备互踢校验。
     */
    public static JwtPayload parsePayload(String token, String secret, String expectedType) {
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
            // sid claim 缺失即返回 null（老 token / 无 deviceId 登录的 token），
            // AuthService 据此跳过单设备互踢校验。
            String sid = claims.get(CLAIM_SID, String.class);
            if (sid != null && sid.isBlank()) {
                sid = null;
            }
            return new JwtPayload(uid, sid);
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

    /**
     * 获取 token 的剩余有效期（毫秒）。
     * <p>不做签名校验 / 类型校验，仅解析 expiration claim。
     * 用于 token 黑名单：加入黑名单时需要知道 token 还有多久过期，以此作为缓存 TTL。
     *
     * @return 剩余毫秒数；token 已过期返回 0，解析失败返回 0
     */
    public static long getRemainingMs(String token, String secret) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return 0;
            }
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (ExpiredJwtException e) {
            // token 已过期，无需加黑名单
            return 0;
        } catch (Exception e) {
            log.warn("解析 token 过期时间失败: {}", e.getClass().getSimpleName());
            return 0;
        }
    }

    // ====== 内部 ======

    private static String generate(String uid, String secret, long expireMs, String type, String sessionId) {
        if (secret == null || secret.length() < 32) {
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING, "JWT 密钥必须至少 32 字符");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        // 用 LinkedHashMap 保留 claim 顺序（仅为 token 字符串可读性，无功能影响）；
        // sessionId == null/blank 时不写 sid claim，让老 token 与新 token 在结构上完全等价。
        Map<String, Object> claimMap = new LinkedHashMap<>(2);
        claimMap.put(CLAIM_TYPE, type);
        if (sessionId != null && !sessionId.isBlank()) {
            claimMap.put(CLAIM_SID, sessionId);
        }

        return Jwts.builder()
                .subject(uid)
                .claims(claimMap)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * JWT 解析结果：包含 uid 和 sessionId（sid claim）。
     * <p>{@code sessionId == null} 表示该 token 不参与单设备互踢校验。
     */
    public record JwtPayload(String uid, String sessionId) {
    }
}
