package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 埋点事件查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackEventQueryRequest {
    /**
     * 用户 ID（与 deviceId 二选一）
     */
    private String userId;

    /**
     * 设备 ID（与 userId 二选一）
     */
    private String deviceId;

    /**
     * 事件 Key（可选，用于按事件名筛选）
     */
    private String eventKey;

    /**
     * 开始时间（毫秒，可选）
     */
    private Long startTs;

    /**
     * 结束时间（毫秒，可选）
     */
    private Long endTs;

    /**
     * 分页：页码（0-based）
     */
    private Integer page = 0;

    /**
     * 分页：每页大小
     */
    private Integer size = 20;
}
