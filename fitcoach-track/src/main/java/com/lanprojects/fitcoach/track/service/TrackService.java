package com.lanprojects.fitcoach.track.service;

import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.security.ClientIpResolver;
import com.lanprojects.fitcoach.track.dto.TrackEventBatchRequest;
import com.lanprojects.fitcoach.track.dto.TrackEventItem;
import com.lanprojects.fitcoach.track.entity.TrackEventEntity;
import com.lanprojects.fitcoach.track.repository.TrackEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 埋点服务：批量入库 + 限流 + 基础信息自动注入。
 *
 * <p><b>处理流程</b>：
 * <ol>
 *   <li><b>限流</b>：deviceId 维度 200 批次/分钟（{@link TrackRateLimiter}）；</li>
 *   <li><b>校验</b>：批次非空 / 数量 ≤ {@link #MAX_BATCH_SIZE} / 每条 eventKey 不为空；</li>
 *   <li><b>组装</b>：从 {@link ClientContext} 注入 deviceId / platform / appVersion / bundleVersion / locale；
 *       userId 由 controller 解析 token 后传入（可空）；</li>
 *   <li><b>落库</b>：批量 saveAll，不开启异步线程池（JPA flush 性能足够；将来真高并发再做异步队列）。</li>
 * </ol>
 *
 * <p><b>降级策略</b>：单条 item 校验失败（eventKey 空、长度超限）会被静默跳过 + warn 日志，
 * 不抛异常 —— 单一脏数据不应阻断整批上报，否则客户端反复重试会越积越多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackService {

    /** 单批次上限（与客户端 SDK 默认刷盘 batch 一致，留 5x 余量给客户端积压） */
    public static final int MAX_BATCH_SIZE = 100;

    /** eventKey 最大长度（与 entity 列定义保持一致） */
    private static final int MAX_EVENT_KEY_LEN = 64;

    private final TrackEventRepository trackEventRepository;
    private final TrackRateLimiter rateLimiter;
    private final GeoIPService geoIPService;

    /**
     * 接收一批埋点。
     *
     * @param uid     当前登录用户 uid（未登录传 null）
     * @param request 批次请求体
     * @return 实际入库的事件数（可能少于 request.events.size()，因为脏数据会被跳过）
     */
    @Transactional
    public int receiveBatch(String uid, TrackEventBatchRequest request) {
        if (request == null || request.getEvents() == null || request.getEvents().isEmpty()) {
            throw new BusinessException(ResultCode.TRACK_BATCH_EMPTY);
        }

        List<TrackEventItem> items = request.getEvents();
        if (items.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.TRACK_BATCH_TOO_LARGE);
        }

        // ====== 1. 限流（deviceId 维度；未登录用户也走这个维度） ======
        String deviceId = ClientContext.deviceId();
        if (!rateLimiter.tryAcquire(deviceId)) {
            log.warn("埋点限流触发: deviceId={}, uid={}, batch={}", deviceId, uid, items.size());
            throw new BusinessException(ResultCode.TRACK_RATE_LIMITED);
        }

        // ====== 2. 一次性从 ClientContext 取基础信息（同一请求所有事件共用） ======
        String platform      = nullSafe(ClientContext.platform());
        String appVersion    = ClientContext.get().nativeVersionName();
        String bundleVersion = ClientContext.get().bundleVersionName();
        String locale        = ClientContext.lang();

        // deviceId / platform 是必填，缺失直接拒（说明客户端 SDK 没接拦截器或被绕过）
        if (deviceId == null || deviceId.isBlank() || platform.isBlank()) {
            log.warn("埋点缺失关键 header: deviceId={}, platform={}, uid={}", deviceId, platform, uid);
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        long serverTs = System.currentTimeMillis();

        // ====== 3. 组装 entity 列表（脏数据跳过） ======
        List<TrackEventEntity> entities = new ArrayList<>(items.size());
        int skipped = 0;
        for (TrackEventItem item : items) {
            if (item == null) { skipped++; continue; }

            String key = item.getEventKey();
            if (key == null || key.isBlank() || key.length() > MAX_EVENT_KEY_LEN) {
                log.warn("跳过非法埋点 eventKey: {}", key);
                skipped++;
                continue;
            }

            TrackEventEntity entity = new TrackEventEntity();
            entity.setEventKey(key);
            entity.setUserId(uid);
            entity.setDeviceId(deviceId);
            entity.setSessionId(safeOrFallback(item.getSessionId(), "unknown"));
            entity.setPlatform(platform);
            entity.setAppVersion(appVersion);
            entity.setBundleVersion(bundleVersion);
            entity.setOsVersion(item.getOsVersion());
            entity.setLocale(locale);
            // region 策略：优先用客户端上报值，缺失时用 GeoIP 兜底
            String region = item.getRegion();
            if (region == null || region.isBlank()) {
                String clientIp = resolveClientIp();
                if (clientIp != null) {
                    String geoRegion = geoIPService.getCountryCodeByIP(clientIp);
                    if (geoRegion != null) {
                        region = geoRegion;
                    }
                }
            }
            entity.setRegion(region);
            entity.setTimezone(item.getTimezone());
            entity.setNetworkType(item.getNetworkType());
            // client_ts 不信，但要存，便于排查客户端时间漂移
            entity.setClientTs(item.getClientTs() != null ? item.getClientTs() : serverTs);
            entity.setServerTs(serverTs);
            entity.setProperties(safeProperties(item.getProperties()));

            entities.add(entity);
        }

        if (entities.isEmpty()) {
            log.warn("整批埋点全部为脏数据，已跳过: batch={}", items.size());
            return 0;
        }

        trackEventRepository.saveAll(entities);

        if (log.isDebugEnabled()) {
            log.debug("埋点入库: uid={}, deviceId={}, received={}, skipped={}",
                    uid, deviceId, entities.size(), skipped);
        }
        return entities.size();
    }

    // ====== Helpers ======

    /**
     * 从当前 HTTP 请求中解析客户端真实 IP（通过 RequestContextHolder + ClientIpResolver）。
     * <p>用于 GeoIP 兜底：当客户端未上报 region 时，根据 IP 反查国家码。
     *
     * @return 客户端 IP；非 HTTP 上下文返回 null
     */
    private static String resolveClientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                return ClientIpResolver.resolve(request);
            }
        } catch (Exception e) {
            // 非 web 上下文（如异步线程），静默返回 null
        }
        return null;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String safeOrFallback(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static Map<String, String> safeProperties(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>();
        }
        // 防御：过滤 null key / value，避免 Jackson 序列化报错
        Map<String, String> clean = new HashMap<>(raw.size());
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            clean.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return clean;
    }
}
