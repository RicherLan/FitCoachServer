package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 漏斗分析步骤响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStepResponse {
    /**
     * 步骤序号（1-based）
     */
    private Integer stepIndex;

    /**
     * 事件 Key
     */
    private String eventKey;

    /**
     * 该步骤的用户数（已登录用户）
     */
    private Long userCount;

    /**
     * 该步骤的设备数（含未登录用户）
     */
    private Long deviceCount;

    /**
     * 相对于上一步的转化率（%）
     */
    private Double conversionRate;

    /**
     * 相对于第一步的累计转化率（%）
     */
    private Double cumulativeConversionRate;
}
