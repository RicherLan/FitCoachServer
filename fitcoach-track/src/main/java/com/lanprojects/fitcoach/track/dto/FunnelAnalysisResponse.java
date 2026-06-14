package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 漏斗分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelAnalysisResponse {
    /**
     * 漏斗名称（如 "支付漏斗"）
     */
    private String funnelName;

    /**
     * 时间窗开始（毫秒）
     */
    private Long startTs;

    /**
     * 时间窗结束（毫秒）
     */
    private Long endTs;

    /**
     * 平台筛选（可选）
     */
    private String platform;

    /**
     * 地区筛选（可选）
     */
    private String region;

    /**
     * 漏斗步骤列表
     */
    private List<FunnelStepResponse> steps;

    /**
     * 总转化率（最后一步 / 第一步）
     */
    private Double totalConversionRate;

    /**
     * 第一步的用户数
     */
    private Long firstStepUserCount;

    /**
     * 最后一步的用户数
     */
    private Long lastStepUserCount;
}
