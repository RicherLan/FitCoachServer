package com.lanprojects.fitcoach.track.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自定义报表请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomReportRequest {
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
     * 选中的指标（pv / uv / deviceUv / conversionRate）
     */
    private List<String> metrics;

    /**
     * 分组维度（eventKey / platform / region / date）
     */
    private String groupBy;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方向（asc / desc）
     */
    private String orderDirection;

    /**
     * 开始时间（毫秒）
     */
    private Long startTs;

    /**
     * 结束时间（毫秒）
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
     * 是否保存为模板
     */
    private Boolean saveAsTemplate;

    /**
     * 模板名称（保存为模板时必填）
     */
    private String templateName;
}
