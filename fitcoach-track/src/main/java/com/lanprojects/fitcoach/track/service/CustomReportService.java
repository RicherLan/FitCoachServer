package com.lanprojects.fitcoach.track.service;

import com.lanprojects.fitcoach.track.dto.CustomReportRequest;
import com.lanprojects.fitcoach.track.dto.CustomReportResponse;
import com.lanprojects.fitcoach.track.repository.TrackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义报表服务
 */
@Service
@RequiredArgsConstructor
public class CustomReportService {
    private final TrackEventRepository trackEventRepository;

    /**
     * 生成自定义报表
     */
    public CustomReportResponse generateReport(CustomReportRequest request) {
        // 1. 查询数据
        List<Map<String, Object>> reportData = queryReportData(request);

        // 2. 计算汇总
        Map<String, Object> summary = calculateSummary(reportData, request.getMetrics());

        // 3. 构建响应
        return CustomReportResponse.builder()
                .reportName(request.getReportName())
                .description(request.getDescription())
                .eventKeys(request.getEventKeys())
                .metrics(request.getMetrics())
                .groupBy(request.getGroupBy())
                .startTs(request.getStartTs())
                .endTs(request.getEndTs())
                .platform(request.getPlatform())
                .region(request.getRegion())
                .data(reportData)
                .summary(summary)
                .generatedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 查询报表数据
     */
    private List<Map<String, Object>> queryReportData(CustomReportRequest request) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 根据分组维度查询数据
        String groupBy = request.getGroupBy();

        if ("eventKey".equals(groupBy)) {
            // 按事件 key 分组
            for (String eventKey : request.getEventKeys()) {
                Map<String, Object> row = new HashMap<>();
                row.put("eventKey", eventKey);

                // 查询各个指标
                for (String metric : request.getMetrics()) {
                    Object value = queryMetric(eventKey, metric, request);
                    row.put(metric, value);
                }

                result.add(row);
            }
        } else if ("platform".equals(groupBy)) {
            // 按平台分组
            for (String platform : Arrays.asList("android", "ios")) {
                Map<String, Object> row = new HashMap<>();
                row.put("platform", platform);

                for (String eventKey : request.getEventKeys()) {
                    for (String metric : request.getMetrics()) {
                        Object value = queryMetricWithPlatform(eventKey, metric, platform, request);
                        row.put(eventKey + "_" + metric, value);
                    }
                }

                result.add(row);
            }
        } else if ("region".equals(groupBy)) {
            // 按地区分组
            for (String region : Arrays.asList("CN", "US", "JP")) {
                Map<String, Object> row = new HashMap<>();
                row.put("region", region);

                for (String eventKey : request.getEventKeys()) {
                    for (String metric : request.getMetrics()) {
                        Object value = queryMetricWithRegion(eventKey, metric, region, request);
                        row.put(eventKey + "_" + metric, value);
                    }
                }

                result.add(row);
            }
        }

        return result;
    }

    /**
     * 查询单个指标
     */
    private Object queryMetric(String eventKey, String metric, CustomReportRequest request) {
        if ("pv".equals(metric)) {
            return trackEventRepository.countByEventKeyAndServerTsBetween(
                    eventKey, request.getStartTs(), request.getEndTs());
        } else if ("uv".equals(metric)) {
            return trackEventRepository.countDistinctUsersByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), null, null);
        } else if ("deviceUv".equals(metric)) {
            return trackEventRepository.countDistinctDevicesByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), null, null);
        } else if ("conversionRate".equals(metric)) {
            long uv = trackEventRepository.countDistinctUsersByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), null, null);
            long pv = trackEventRepository.countByEventKeyAndServerTsBetween(
                    eventKey, request.getStartTs(), request.getEndTs());
            return pv > 0 ? (double) uv / pv * 100 : 0;
        }
        return 0;
    }

    /**
     * 查询带平台筛选的指标
     */
    private Object queryMetricWithPlatform(String eventKey, String metric, String platform, CustomReportRequest request) {
        if ("pv".equals(metric)) {
            return trackEventRepository.countByEventKeyAndServerTsBetweenAndPlatform(
                    eventKey, request.getStartTs(), request.getEndTs(), platform);
        } else if ("uv".equals(metric)) {
            return trackEventRepository.countDistinctUsersByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), platform, null);
        } else if ("deviceUv".equals(metric)) {
            return trackEventRepository.countDistinctDevicesByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), platform, null);
        }
        return 0;
    }

    /**
     * 查询带地区筛选的指标
     */
    private Object queryMetricWithRegion(String eventKey, String metric, String region, CustomReportRequest request) {
        if ("pv".equals(metric)) {
            return trackEventRepository.countByEventKeyAndServerTsBetweenAndRegion(
                    eventKey, request.getStartTs(), request.getEndTs(), region);
        } else if ("uv".equals(metric)) {
            return trackEventRepository.countDistinctUsersByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), null, region);
        } else if ("deviceUv".equals(metric)) {
            return trackEventRepository.countDistinctDevicesByEventKey(
                    eventKey, request.getStartTs(), request.getEndTs(), null, region);
        }
        return 0;
    }

    /**
     * 计算汇总行
     */
    private Map<String, Object> calculateSummary(List<Map<String, Object>> data, List<String> metrics) {
        Map<String, Object> summary = new HashMap<>();

        if (data.isEmpty()) {
            return summary;
        }

        // 对每个指标求和
        for (String metric : metrics) {
            long total = 0;
            for (Map<String, Object> row : data) {
                Object value = row.get(metric);
                if (value instanceof Number) {
                    total += ((Number) value).longValue();
                }
            }
            summary.put(metric, total);
        }

        return summary;
    }
}
