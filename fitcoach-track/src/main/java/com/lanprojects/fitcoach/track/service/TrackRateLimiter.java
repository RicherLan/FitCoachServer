package com.lanprojects.fitcoach.track.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 埋点上报速率限流器（per-deviceId，本地 Caffeine 内存实现）。
 *
 * <p><b>策略</b>：单 deviceId 每 60 秒最多 200 次「批次请求」（不是事件数）。
 * 客户端 SDK 内置 30s/20 条节流后，正常用户远低于此阈值；
 * 这个上限只挡两类异常：客户端 Bug 死循环 + 恶意刷量。
 *
 * <p><b>与 LoginAttemptLimiter 的差异</b>：登录限流是"失败计数 + 成功 reset"语义；
 * 埋点限流是"无差别计数"语义 —— 不分成功失败，每次调用 +1。所以单独写一个。
 *
 * <p><b>注意</b>：单实例进程内有效。多副本部署后总配额是 200*N（N=副本数），通常仍然能挡住攻击；
 * 严格全局限流需迁 Redis lua（短期没必要）。
 */
@Component
public class TrackRateLimiter {

    /** 单 deviceId 在 window 内允许的最大批次数 */
    private static final int MAX_PER_WINDOW = 200;

    /** 限流时间窗口 */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** 最多缓存多少个 deviceId（防止内存被恶意 key 撑爆） */
    private static final int MAX_KEYS = 100_000;

    private final Cache<String, AtomicInteger> store = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW)
            .maximumSize(MAX_KEYS)
            .build();

    /**
     * 尝试通过限流：
     * <ul>
     *   <li>计数 +1；</li>
     *   <li>累加后超过上限 → 返回 false（拒绝）；</li>
     *   <li>否则返回 true（放行）。</li>
     * </ul>
     *
     * @param deviceId 设备唯一标识（null/blank 直接放行 —— 由上游兜底）
     */
    public boolean tryAcquire(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return true;
        }
        AtomicInteger counter = store.get(deviceId, k -> new AtomicInteger(0));
        int after = counter.incrementAndGet();
        return after <= MAX_PER_WINDOW;
    }

    public int getMaxPerWindow() {
        return MAX_PER_WINDOW;
    }

    public Duration getWindow() {
        return WINDOW;
    }
}
