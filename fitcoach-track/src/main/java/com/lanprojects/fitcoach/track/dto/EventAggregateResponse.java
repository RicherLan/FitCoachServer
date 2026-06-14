package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事件聚合统计响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAggregateResponse {
    /**
     * 事件 Key
     */
    private String eventKey;

    /**
     * 页面浏览量（PV）
     */
    private Long pv;

    /**
     * 独立用户数（UV，已登录用户）
     */
    private Long uv;

    /**
     * 独立设备数（含未登录用户）
     */
    private Long deviceUv;
}
