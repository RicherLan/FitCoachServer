package com.lanprojects.fitcoach.track.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.track.dto.FunnelAnalysisResponse;
import com.lanprojects.fitcoach.track.service.FunnelAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 漏斗分析接口
 *
 * 权限：仅 Admin 角色可访问
 */
@RestController
@RequestMapping("/api/admin/track/funnel")
@RequiredArgsConstructor
public class FunnelAnalysisController {
    private final FunnelAnalysisService funnelAnalysisService;

    /**
     * 分析漏斗转化率
     *
     * @param funnelName 漏斗名称（如 "支付漏斗"）
     * @param steps 漏斗步骤（逗号分隔的事件 key，如 "payment_view_plans,payment_click_plan,payment_start,payment_success"）
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒）
     * @param platform 平台筛选（可选，android / ios）
     * @param region 地区筛选（可选，CN / US / ...）
     * @return 漏斗分析结果
     */
    @GetMapping("/analyze")
    public Result<FunnelAnalysisResponse> analyzeFunnel(
            @RequestParam String funnelName,
            @RequestParam String steps,
            @RequestParam Long startTs,
            @RequestParam Long endTs,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String region) {

        // 解析步骤列表
        List<String> stepList = List.of(steps.split(","));

        FunnelAnalysisResponse result = funnelAnalysisService.analyzeFunnel(
                funnelName, stepList, startTs, endTs, platform, region);

        return Result.success(result);
    }

    /**
     * 预定义漏斗：支付漏斗
     */
    @GetMapping("/preset/payment")
    public Result<FunnelAnalysisResponse> paymentFunnel(
            @RequestParam Long startTs,
            @RequestParam Long endTs,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String region) {

        List<String> steps = List.of(
                "payment_view_plans",
                "payment_click_plan",
                "payment_start",
                "payment_success"
        );

        FunnelAnalysisResponse result = funnelAnalysisService.analyzeFunnel(
                "支付漏斗", steps, startTs, endTs, platform, region);

        return Result.success(result);
    }

    /**
     * 预定义漏斗：训练漏斗
     */
    @GetMapping("/preset/training")
    public Result<FunnelAnalysisResponse> trainingFunnel(
            @RequestParam Long startTs,
            @RequestParam Long endTs,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String region) {

        List<String> steps = List.of(
                "home_view",
                "training_start",
                "training_complete_set",
                "training_finish"
        );

        FunnelAnalysisResponse result = funnelAnalysisService.analyzeFunnel(
                "训练漏斗", steps, startTs, endTs, platform, region);

        return Result.success(result);
    }

    /**
     * 预定义漏斗：会员转化漏斗
     */
    @GetMapping("/preset/membership")
    public Result<FunnelAnalysisResponse> membershipFunnel(
            @RequestParam Long startTs,
            @RequestParam Long endTs,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String region) {

        List<String> steps = List.of(
                "home_view",
                "home_click_membership",
                "payment_view_plans",
                "payment_success"
        );

        FunnelAnalysisResponse result = funnelAnalysisService.analyzeFunnel(
                "会员转化漏斗", steps, startTs, endTs, platform, region);

        return Result.success(result);
    }
}
