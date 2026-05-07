package com.lanprojects.fitcoach.admin.util;

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
 * Admin 端 JWT 工具。
 * <p>
 * 与客户端 {@code JwtUtils} 完全独立 —— sub=username (而非 uid)，并通过
 * {@code claim "type" = "admin_access"} 与客户端 token 严格区分，杜绝
 * "拿用户 token 打管理员接口" / "管理员 token 越界访问用户接口"两类越权风险。
 * <p>
 * 密钥复用 sys_config 中的 {@code jwt.secret} —— 同一实例上两套 token 共用密钥
 * 但 type claim 不同，已经足够隔离；后续若要彻底物理隔离，可以新增 {@code jwt.admin_secret}。
 */
@Slf4j
public class AdminJwtUtils {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLE = "role";
    public static final String TYPE_ADMIN_ACCESS = "admin_access";

    private AdminJwtUtils() {
    }

    /**
     * 生成 admin access token
     *
     * @param username 管理员账号（写到 sub）
     * @param role     角色字符串（写到 claim，便于拦截器快速读取无需查 DB）
     * @param secret   签名密钥
     * @param expireMs 过期时长（毫秒）
     */
    public static String generateAccessToken(String username, String role, String secret, long expireMs) {
        if (secret == null || secret.length() < 32) {
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING, "JWT 密钥必须至少 32 字符");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);
        return Jwts.builder()
                .subject(username)
                .claims(Map.of(CLAIM_TYPE, TYPE_ADMIN_ACCESS, CLAIM_ROLE, role))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 admin token 并校验类型；任何失败都抛 BusinessException。
     *
     * @return 解析出的 username + role 二元组
     */
    public static AdminTokenPayload parseAndVerify(String token, String secret) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.ADMIN_UNAUTHORIZED, "缺少访问凭证");
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TYPE, String.class);
            if (!TYPE_ADMIN_ACCESS.equals(type)) {
                log.warn("Admin JWT 类型不匹配, expected={}, actual={}", TYPE_ADMIN_ACCESS, type);
                throw new BusinessException(ResultCode.ADMIN_TOKEN_INVALID);
            }
            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                throw new BusinessException(ResultCode.ADMIN_TOKEN_INVALID);
            }
            String role = claims.get(CLAIM_ROLE, String.class);
            return new AdminTokenPayload(username, role);
        } catch (ExpiredJwtException e) {
            log.warn("Admin JWT 已过期");
            throw new BusinessException(ResultCode.ADMIN_UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Admin JWT 解析失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ADMIN_TOKEN_INVALID);
        }
    }

    /** Admin token 解析结果 */
    public record AdminTokenPayload(String username, String role) {
    }
}
