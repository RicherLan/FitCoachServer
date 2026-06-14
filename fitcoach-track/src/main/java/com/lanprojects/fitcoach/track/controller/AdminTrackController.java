package com.lanprojects.fitcoach.track.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.track.dto.EventAggregateResponse;
import com.lanprojects.fitcoach.track.dto.TrackEventQueryRequest;
import com.lanprojects.fitcoach.track.dto.TrackEventResponse;
import com.lanprojects.fitcoach.track.service.AdminTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin 后台埋点查询接口
 *
 * 权限：仅 Admin 角色可访问
 */
@RestController
@RequestMapping("/api/admin/track")
@RequiredArgsConstructor
public class AdminTrackController {
    private final AdminTrackService adminTrackService;

    /**
     * 查询事件流（按用户或设备）
     *
     * @param userId 用户 ID（与 deviceId 二选一）
     * @param deviceId 设备 ID（与 userId 二选一）
     * @param page 页码（0-based）
     * @param size 每页大小
     * @return 事件列表（分页）
     */
    @GetMapping("/events")
    public Result<Page<TrackEventResponse>> queryEvents(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        TrackEventQueryRequest request = TrackEventQueryRequest.builder()
                .userId(userId)
                .deviceId(deviceId)
                .page(page)
                .size(size)
                .build();

        Page<TrackEventResponse> result = adminTrackService.queryEvents(request);
        return Result.success(result);
    }

    /**
     * 查询事件总览（按时间窗聚合）
     *
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒）
     * @return 事件聚合统计列表（按 PV 倒序）
     */
    @GetMapping("/overview")
    public Result<List<EventAggregateResponse>> queryOverview(
            @RequestParam Long startTs,
            @RequestParam Long endTs) {

        List<EventAggregateResponse> result = adminTrackService.queryOverview(startTs, endTs);
        return Result.success(result);
    }

    /**
     * 查询完整会话（漏斗分析基础）
     *
     * @param sessionId 会话 ID
     * @return 该会话的完整事件序列（时间正序）
     */
    @GetMapping("/session/{sessionId}")
    public Result<List<TrackEventResponse>> querySession(
            @PathVariable String sessionId) {

        List<TrackEventResponse> result = adminTrackService.querySession(sessionId);
        return Result.success(result);
    }
}
