package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 自定义报表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomReportResponse {
    /**
     * 报表名称
     */
    private String reportName;

    /**
     * 报表描述
     */
    private String description;

    /**
     * 选中的事件 key 列表
     */
    private List<String> eventKeys;

    /**
     * 选中的指标
     */
    private List<String> metrics;

    /**
     * 分组维度
     */
    private String groupBy;

    /**
     * 时间范围
     */
    private Long startTs;
    private Long endTs;

    /**
     * 筛选条件
     */
    private String platform;
    private String region;

    /**
     * 报表数据（行列式）
     * 每行是一个数据点，包含分组字段和各个指标
     */
    private List<Map<String, Object>> data;

    /**
     * 汇总行（总计）
     */
    private Map<String, Object> summary;

    /**
     * 生成时间
     */
    private Long generatedAt;
}
