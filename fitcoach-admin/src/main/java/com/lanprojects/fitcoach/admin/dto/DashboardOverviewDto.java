package com.lanprojects.fitcoach.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Dashboard 概览统计数据。
 * <p>所有数字单口径返回，前端可直接渲染卡片，无需额外计算。
 */
@Data
@Builder
public class DashboardOverviewDto {
    /** 用户总数 */
    private long totalUsers;
    /** 启用中用户数 */
    private long activeUsers;
    /** 今日新增用户数 */
    private long newUsersToday;
    /** 近 7 天新增用户数 */
    private long newUsersLast7Days;
    /** 近 30 天新增用户数 */
    private long newUsersLast30Days;

    /** 反馈总数 */
    private long totalFeedbacks;
    /** 待处理反馈数 */
    private long pendingFeedbacks;
    /** 处理中反馈数 */
    private long processingFeedbacks;
    /** 已解决反馈数 */
    private long resolvedFeedbacks;
    /** 已忽略反馈数 */
    private long ignoredFeedbacks;

    /** 反馈按类型分布：key=SUGGESTION/EXPERIENCE/OTHER, value=count */
    private Map<String, Long> feedbacksByType;

    /** 服务器时间（毫秒），便于前端校时 */
    private long serverTime;
}
