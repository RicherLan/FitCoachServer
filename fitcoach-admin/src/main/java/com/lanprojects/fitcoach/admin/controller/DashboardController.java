package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.dto.DashboardOverviewDto;
import com.lanprojects.fitcoach.admin.service.DashboardService;
import com.lanprojects.fitcoach.common.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台首页 Dashboard 接口。
 * <p>路径前缀：/api/admin/dashboard
 * <ul>
 *   <li>{@code GET /overview} —— 概览数据（用户 + 反馈聚合）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewDto> overview() {
        return Result.success(dashboardService.getOverview());
    }
}
