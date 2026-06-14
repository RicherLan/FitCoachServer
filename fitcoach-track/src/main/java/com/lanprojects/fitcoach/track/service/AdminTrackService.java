package com.lanprojects.fitcoach.track.service;

import com.lanprojects.fitcoach.track.dto.EventAggregateResponse;
import com.lanprojects.fitcoach.track.dto.TrackEventQueryRequest;
import com.lanprojects.fitcoach.track.dto.TrackEventResponse;
import com.lanprojects.fitcoach.track.entity.TrackEventEntity;
import com.lanprojects.fitcoach.track.repository.TrackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin 后台埋点查询服务
 */
@Service
@RequiredArgsConstructor
public class AdminTrackService {
    private final TrackEventRepository trackEventRepository;

    /**
     * 查询事件流（按用户或设备）
     */
    public Page<TrackEventResponse> queryEvents(TrackEventQueryRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<TrackEventEntity> page;
        if (request.getUserId() != null && !request.getUserId().isEmpty()) {
            // 按用户查询
            page = trackEventRepository.findByUserIdOrderByServerTsDesc(
                    request.getUserId(), pageable);
        } else if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
            // 按设备查询
            page = trackEventRepository.findByDeviceIdOrderByServerTsDesc(
                    request.getDeviceId(), pageable);
        } else {
            // 都没指定，返回空
            return Page.empty(pageable);
        }

        return page.map(this::toResponse);
    }

    /**
     * 查询事件总览（按时间窗聚合）
     */
    public List<EventAggregateResponse> queryOverview(Long startTs, Long endTs) {
        // 查询 PV + UV（已登录用户）
        List<TrackEventRepository.EventAggregateProjection> pvUvList =
                trackEventRepository.aggregateOverview(startTs, endTs);

        // 查询 deviceUv（含未登录用户）
        List<TrackEventRepository.DeviceUvProjection> deviceUvList =
                trackEventRepository.aggregateDeviceUv(startTs, endTs);

        // 合并两个结果
        Map<String, EventAggregateResponse> resultMap = new HashMap<>();
        for (var pvUv : pvUvList) {
            resultMap.put(pvUv.getEventKey(), EventAggregateResponse.builder()
                    .eventKey(pvUv.getEventKey())
                    .pv(pvUv.getPv())
                    .uv(pvUv.getUv())
                    .build());
        }

        for (var deviceUv : deviceUvList) {
            resultMap.computeIfPresent(deviceUv.getEventKey(), (k, v) -> {
                v.setDeviceUv(deviceUv.getDeviceUv());
                return v;
            });
        }

        // 按 PV 倒序排列
        return resultMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getPv(), a.getPv()))
                .collect(Collectors.toList());
    }

    /**
     * 查询完整会话（漏斗分析基础）
     */
    public List<TrackEventResponse> querySession(String sessionId) {
        List<TrackEventEntity> entities = trackEventRepository.findBySessionIdOrderByServerTsAsc(sessionId);
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Entity → Response DTO
     */
    private TrackEventResponse toResponse(TrackEventEntity entity) {
        return TrackEventResponse.builder()
                .id(entity.getId())
                .eventKey(entity.getEventKey())
                .userId(entity.getUserId())
                .deviceId(entity.getDeviceId())
                .sessionId(entity.getSessionId())
                .platform(entity.getPlatform())
                .appVersion(entity.getAppVersion())
                .bundleVersion(entity.getBundleVersion())
                .osVersion(entity.getOsVersion())
                .locale(entity.getLocale())
                .region(entity.getRegion())
                .timezone(entity.getTimezone())
                .networkType(entity.getNetworkType())
                .clientTs(entity.getClientTs())
                .serverTs(entity.getServerTs())
                .properties(entity.getProperties())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
