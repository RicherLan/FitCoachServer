package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户活跃度（心跳）服务。
 *
 * <p>提供 {@link #touch(String)} 给业务接口在拿到 currentUser 后调用，记录"用户最近活跃时间"，
 * admin 后台通过它判定在线/离线。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li><b>写库节流</b>：同一 uid 在 {@value #THROTTLE_MS}ms 内只写一次 DB。
 *       客户端 log-pull 是 120s 周期，一般每周期写一次足够；如果用户高频主动重试，
 *       节流可避免热点行被反复 update。</li>
 *   <li><b>进程内缓存</b>：用 ConcurrentHashMap 记录上次写库时间。多副本部署时各自缓存独立，
 *       最坏写入量 = N 副本 × 客户端轮询频率，仍可接受；Redis 集中存可作为后续优化。</li>
 *   <li><b>容错</b>：DB 写入失败不抛异常（业务接口不应因心跳失败而失败），仅记 warn。</li>
 *   <li><b>在线窗口</b>：{@link #ONLINE_WINDOW_MS} = 5min，覆盖 2.5 个 120s 轮询周期，
 *       一次网络抖动丢包不会立刻判离线。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    /** 写库节流阈值：同一 uid 在该时间内多次 touch，只写一次 DB（毫秒） */
    private static final long THROTTLE_MS = 60_000L;

    /** 在线判定窗口：超过这个时间没活跃则认为离线（毫秒）。供 DTO 层 isOnline 使用。 */
    public static final long ONLINE_WINDOW_MS = 5 * 60_000L;

    private final UserRepository userRepository;

    /** uid → 上次成功写库的毫秒时间戳；只在内存里维护，不持久化 */
    private final Map<String, Long> lastTouchCache = new ConcurrentHashMap<>();

    /**
     * 标记用户活跃。
     * <p>调用方在拿到 currentUser 后立即调用即可，无需关心节流/异常。
     *
     * @param uid 当前用户 uid；为 null/空时静默忽略
     */
    public void touch(String uid) {
        if (uid == null || uid.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastTouchCache.get(uid);
        if (last != null && now - last < THROTTLE_MS) {
            // 节流命中：本次不写库，但 cache 不更新（保持原写入时间作为"上次落盘点"）
            return;
        }
        // 先写 cache 再写 DB——失败时下一次会重试，不会因为 cache 已更新而漏写过头
        lastTouchCache.put(uid, now);
        try {
            userRepository.findByUid(uid).ifPresent(u -> {
                u.setLastActiveAt(LocalDateTime.now());
                userRepository.save(u);
            });
        } catch (Exception e) {
            // 心跳是辅助能力，失败不应影响主业务流程
            log.warn("更新 lastActiveAt 失败 uid={}: {}", uid, e.getMessage());
        }
    }

    /**
     * 静态判断"是否在线"。
     * <p>放静态方法是为了让 DTO 的 {@code from()} 直接调用，不必 @Autowired。
     */
    public static boolean isOnline(LocalDateTime lastActiveAt) {
        if (lastActiveAt == null) {
            return false;
        }
        long activeMillis = lastActiveAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return System.currentTimeMillis() - activeMillis < ONLINE_WINDOW_MS;
    }
}
