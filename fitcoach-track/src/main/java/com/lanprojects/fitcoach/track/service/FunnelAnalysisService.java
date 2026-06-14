package com.lanprojects.fitcoach.track.service;

import com.lanprojects.fitcoach.track.dto.FunnelAnalysisResponse;
import com.lanprojects.fitcoach.track.dto.FunnelStepResponse;
import com.lanprojects.fitcoach.track.repository.TrackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 漏斗分析服务
 */
@Service
@RequiredArgsConstructor
public class FunnelAnalysisService {
    private final TrackEventRepository trackEventRepository;

    /**
     * 分析漏斗转化率
     *
     * @param funnelName 漏斗名称
     * @param steps 漏斗步骤（事件 key 列表）
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒）
     * @param platform 平台筛选（可选）
     * @param region 地区筛选（可选）
     * @return 漏斗分析结果
     */
    public FunnelAnalysisResponse analyzeFunnel(
            String funnelName,
            List<String> steps,
            Long startTs,
            Long endTs,
            String platform,
            String region) {

        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("漏斗步骤不能为空");
        }

        // 查询每个步骤的用户数和设备数
        Map<String, Long> userCountMap = new HashMap<>();
        Map<String, Long> deviceCountMap = new HashMap<>();

        for (String eventKey : steps) {
            // 查询该事件的用户数（已登录用户）
            long userCount = trackEventRepository.countDistinctUsersByEventKey(
                    eventKey, startTs, endTs, platform, region);
            userCountMap.put(eventKey, userCount);

            // 查询该事件的设备数（含未登录用户）
            long deviceCount = trackEventRepository.countDistinctDevicesByEventKey(
                    eventKey, startTs, endTs, platform, region);
            deviceCountMap.put(eventKey, deviceCount);
        }

        // 构建漏斗步骤
        List<FunnelStepResponse> funnelSteps = new ArrayList<>();
        long prevUserCount = 0;
        long firstStepUserCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            String eventKey = steps.get(i);
            long userCount = userCountMap.get(eventKey);
            long deviceCount = deviceCountMap.get(eventKey);

            if (i == 0) {
                firstStepUserCount = userCount;
            }

            // 计算转化率
            double conversionRate = 0;
            double cumulativeConversionRate = 0;

            if (i == 0) {
                conversionRate = 100.0;
                cumulativeConversionRate = 100.0;
            } else {
                if (prevUserCount > 0) {
                    conversionRate = (double) userCount / prevUserCount * 100;
                }
                if (firstStepUserCount > 0) {
                    cumulativeConversionRate = (double) userCount / firstStepUserCount * 100;
                }
            }

            funnelSteps.add(FunnelStepResponse.builder()
                    .stepIndex(i + 1)
                    .eventKey(eventKey)
                    .userCount(userCount)
                    .deviceCount(deviceCount)
                    .conversionRate(Math.round(conversionRate * 100.0) / 100.0)
                    .cumulativeConversionRate(Math.round(cumulativeConversionRate * 100.0) / 100.0)
                    .build());

            prevUserCount = userCount;
        }

        // 计算总转化率
        long lastStepUserCount = prevUserCount;
        double totalConversionRate = 0;
        if (firstStepUserCount > 0) {
            totalConversionRate = (double) lastStepUserCount / firstStepUserCount * 100;
        }

        return FunnelAnalysisResponse.builder()
                .funnelName(funnelName)
                .startTs(startTs)
                .endTs(endTs)
                .platform(platform)
                .region(region)
                .steps(funnelSteps)
                .totalConversionRate(Math.round(totalConversionRate * 100.0) / 100.0)
                .firstStepUserCount(firstStepUserCount)
                .lastStepUserCount(lastStepUserCount)
                .build();
    }
}
