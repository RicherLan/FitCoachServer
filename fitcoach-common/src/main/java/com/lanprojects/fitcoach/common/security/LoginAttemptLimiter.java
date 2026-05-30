package com.lanprojects.fitcoach.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用登录尝试本地限流器（Caffeine 内存实现，单实例进程内有效）。
 *
 * <p>使用场景：手机号 / 用户名 / IP 维度的密码登录、admin 登录、敏感操作鉴权失败计数。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   // 限流器：phone 维度，5 次失败 / 15 分钟
 *   private final LoginAttemptLimiter phoneLimiter =
 *           new LoginAttemptLimiter(5, Duration.ofMinutes(15), 100_000);
 *
 *   public LoginResponse login(String phone, String password) {
 *       if (!phoneLimiter.isAllowed(phone)) throw new BusinessException(LOGIN_RATE_LIMITED);
 *       try {
 *           // ... 校验逻辑
 *           // 校验通过：
 *           phoneLimiter.reset(phone);
 *           return ...;
 *       } catch (BusinessException e) {
 *           phoneLimiter.recordFailure(phone);
 *           throw e;
 *       }
 *   }
 * </pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>跨实例不一致 —— 多副本部署需迁 Redis；当前 V1 单机够用；</li>
 *   <li>失败计数在 window 内累加，窗口过期由 Caffeine 自动清理；</li>
 *   <li>本类<b>不直接依赖</b> ResultCode / BusinessException —— 避免反向依赖；
 *       由调用方根据业务语义包装异常码。</li>
 * </ul>
 */
public class LoginAttemptLimiter {

    private final Cache<String, AtomicInteger> store;
    private final int maxAttempts;
    private final Duration window;

    /**
     * @param maxAttempts 单 key 在 window 内的最大失败次数（达此值后 isAllowed 返回 false）
     * @param window      失败计数的时间窗口（自首次失败开始计时；Caffeine expireAfterWrite）
     * @param maxKeys     最多缓存多少个 key（防止恶意构造大量 key 撑爆内存）
     */
    public LoginAttemptLimiter(int maxAttempts, Duration window, int maxKeys) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("maxKeys must be > 0");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.store = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(maxKeys)
                .build();
    }

    /** 当前窗口内的失败次数（key 为 null/blank 或不存在时视为 0） */
    public int currentCount(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        AtomicInteger counter = store.getIfPresent(key);
        return counter == null ? 0 : counter.get();
    }

    /** 是否仍允许尝试（true=允许，false=已达上限） */
    public boolean isAllowed(String key) {
        return currentCount(key) < maxAttempts;
    }

    /**
     * 登录失败后调用：累加计数；返回累加后的次数。
     *
     * <p>注意：当前实现是"窗口 expireAfterWrite"模式 —— 同一 key 首次失败开始计时，
     * 直到窗口结束才会重新归零；窗口内的累计失败次数与时间长短无关。
     */
    public int recordFailure(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        AtomicInteger counter = store.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    /** 登录成功后调用：清零计数（key 无效时静默忽略） */
    public void reset(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        store.invalidate(key);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getWindow() {
        return window;
    }
}
