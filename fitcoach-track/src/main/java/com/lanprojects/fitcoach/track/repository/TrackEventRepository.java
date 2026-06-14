package com.lanprojects.fitcoach.track.repository;

import com.lanprojects.fitcoach.track.entity.TrackEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 埋点事件 Repository。
 *
 * <p>Phase 1 只暴露最常用的查询：
 * <ul>
 *   <li>按用户/设备查事件流（admin 后台「事件流」页用）；</li>
 *   <li>按 event_key + 时间窗聚合（admin 后台「事件总览」页用）。</li>
 * </ul>
 * 更复杂的趋势 / 漏斗在 Phase 2 用 native query 或换 ClickHouse。
 */
@Repository
public interface TrackEventRepository extends JpaRepository<TrackEventEntity, Long> {

    /**
     * 按用户拉时间倒序事件流。
     * <p>查询走 {@code idx_user_ts} 索引。
     */
    Page<TrackEventEntity> findByUserIdOrderByServerTsDesc(String userId, Pageable pageable);

    /**
     * 按设备拉时间倒序事件流（含未登录 + 已登录的全部事件）。
     * <p>查询走 {@code idx_device_ts} 索引；admin 通过 deviceId 排查特定设备问题。
     */
    Page<TrackEventEntity> findByDeviceIdOrderByServerTsDesc(String deviceId, Pageable pageable);

    /**
     * 按事件 key + 时间窗拉列表（趋势页或验证刚上线的埋点用）。
     * <p>查询走 {@code idx_event_ts} 索引。
     */
    Page<TrackEventEntity> findByEventKeyAndServerTsBetweenOrderByServerTsDesc(
            String eventKey, Long startTs, Long endTs, Pageable pageable);

    /**
     * 按 sessionId 查整个会话的完整事件序列（漏斗分析基础）。
     */
    List<TrackEventEntity> findBySessionIdOrderByServerTsAsc(String sessionId);

    /**
     * 统计时间窗内每个 event_key 的总数 + UV。
     * <p>{@code COUNT(DISTINCT user_id)} 仅统计已登录用户；
     * 未登录的 UV 用 device_id 维度统计（见 {@link #aggregateDeviceUv}）。
     */
    @Query("""
        SELECT e.eventKey AS eventKey,
               COUNT(e) AS pv,
               COUNT(DISTINCT e.userId) AS uv
        FROM TrackEventEntity e
        WHERE e.serverTs BETWEEN :startTs AND :endTs
        GROUP BY e.eventKey
        ORDER BY COUNT(e) DESC
        """)
    List<EventAggregateProjection> aggregateOverview(
            @Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /** 含未登录的 UV：用 device_id 去重 */
    @Query("""
        SELECT e.eventKey AS eventKey,
               COUNT(DISTINCT e.deviceId) AS deviceUv
        FROM TrackEventEntity e
        WHERE e.serverTs BETWEEN :startTs AND :endTs
        GROUP BY e.eventKey
        """)
    List<DeviceUvProjection> aggregateDeviceUv(
            @Param("startTs") Long startTs, @Param("endTs") Long endTs);

    /** Spring Data interface projection — 不需要单独写 DTO 类 */
    interface EventAggregateProjection {
        String getEventKey();
        Long getPv();
        Long getUv();
    }

    interface DeviceUvProjection {
        String getEventKey();
        Long getDeviceUv();
    }

    /**
     * 按事件 key + 时间窗 + 平台 + 地区查询不同用户数（漏斗分析用）
     */
    @Query("""
        SELECT COUNT(DISTINCT e.userId)
        FROM TrackEventEntity e
        WHERE e.eventKey = :eventKey
          AND e.serverTs BETWEEN :startTs AND :endTs
          AND (:platform IS NULL OR e.platform = :platform)
          AND (:region IS NULL OR e.region = :region)
        """)
    long countDistinctUsersByEventKey(
            @Param("eventKey") String eventKey,
            @Param("startTs") Long startTs,
            @Param("endTs") Long endTs,
            @Param("platform") String platform,
            @Param("region") String region);

    /**
     * 按事件 key + 时间窗 + 平台 + 地区查询不同设备数（漏斗分析用）
     */
    @Query("""
        SELECT COUNT(DISTINCT e.deviceId)
        FROM TrackEventEntity e
        WHERE e.eventKey = :eventKey
          AND e.serverTs BETWEEN :startTs AND :endTs
          AND (:platform IS NULL OR e.platform = :platform)
          AND (:region IS NULL OR e.region = :region)
        """)
    long countDistinctDevicesByEventKey(
            @Param("eventKey") String eventKey,
            @Param("startTs") Long startTs,
            @Param("endTs") Long endTs,
            @Param("platform") String platform,
            @Param("region") String region);

    /**
     * 按事件 key + 时间窗查询总数（自定义报表用）
     */
    long countByEventKeyAndServerTsBetween(String eventKey, Long startTs, Long endTs);

    /**
     * 按事件 key + 时间窗 + 平台查询总数（自定义报表用）
     */
    @Query("""
        SELECT COUNT(e)
        FROM TrackEventEntity e
        WHERE e.eventKey = :eventKey
          AND e.serverTs BETWEEN :startTs AND :endTs
          AND e.platform = :platform
        """)
    long countByEventKeyAndServerTsBetweenAndPlatform(
            @Param("eventKey") String eventKey,
            @Param("startTs") Long startTs,
            @Param("endTs") Long endTs,
            @Param("platform") String platform);

    /**
     * 按事件 key + 时间窗 + 地区查询总数（自定义报表用）
     */
    @Query("""
        SELECT COUNT(e)
        FROM TrackEventEntity e
        WHERE e.eventKey = :eventKey
          AND e.serverTs BETWEEN :startTs AND :endTs
          AND e.region = :region
        """)
    long countByEventKeyAndServerTsBetweenAndRegion(
            @Param("eventKey") String eventKey,
            @Param("startTs") Long startTs,
            @Param("endTs") Long endTs,
            @Param("region") String region);
}
