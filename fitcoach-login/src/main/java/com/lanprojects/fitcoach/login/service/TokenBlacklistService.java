package com.lanprojects.fitcoach.login.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.stereotype.Service;

/**
 * Token 黑名单服务 — 让登出真正生效。
 * <p>
 * 当用户调用 logout 时，将当前 access token 和 refresh token 加入黑名单，
 * 后续请求携带该 token 将被拒绝（在 {@code AuthService.parseAndAssertSession()} 中校验）。
 * <p>
 * <b>实现方案</b>：Caffeine 本地缓存 + 按 token 剩余有效期自动过期。
 * <ul>
 *   <li>单机部署场景完全够用；</li>
 *   <li>token 过期后缓存条目自动移除，不浪费内存；</li>
 *   <li>未来多实例部署时可替换为 Redis 实现。</li>
 * </ul>
 */
@Slf4j
@Service
public class TokenBlacklistService {

    /**
     * 黑名单缓存：key = token 字符串, value = true（占位）。
     * <p>使用 variable expiration：每个 token 按剩余有效期独立设置 TTL。
     * 最大容量 10_000（单机场景下，假设 access 2h 过期、每秒 1 次登出 → 2h 最多 7200 条，留充足余量）。
     */
    private final Cache<String, Boolean> blacklist = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new Expiry<String, Boolean>() {
                @Override
                public long expireAfterCreate(String key, Boolean value, long currentTime) {
                    // TTL 在 put 时通过 policy().expireVariably() 设置，
                    // 这里返回一个兜底值（不应被命中，因为我们用 policy 显式设置）
                    return Long.MAX_VALUE;
                }

                @Override
                public long expireAfterUpdate(String key, Boolean value, long currentTime,
                                              @NonNegative long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Boolean value, long currentTime,
                                            @NonNegative long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /**
     * 将 token 加入黑名单。
     *
     * @param token       要拉黑的 token 字符串
     * @param remainingMs token 剩余有效期（毫秒）；过期后缓存自动移除。
     *                    如果 ≤ 0 说明 token 已过期，无需加黑名单（自然失效）。
     */
    public void blacklist(String token, long remainingMs) {
        if (token == null || token.isBlank() || remainingMs <= 0) {
            return;
        }
        // 使用 policy().expireVariably() 设置精确的 per-entry TTL
        blacklist.put(token, Boolean.TRUE);
        blacklist.policy().expireVariably().ifPresent(policy ->
                policy.setExpiresAfter(token, java.time.Duration.ofMillis(remainingMs)));
        log.debug("token 已加入黑名单, remainingMs={}", remainingMs);
    }

    /**
     * 检查 token 是否在黑名单中。
     *
     * @param token token 字符串
     * @return true = 已被拉黑（不允许使用）
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return blacklist.getIfPresent(token) != null;
    }
}
