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

    // ==================== 阶段 6 波 1 flavor 维度补齐 ====================
    // 说明：3 个 Map 的 key 都固定为 CN / GLOBAL / UNKNOWN 三档；UNKNOWN 对应 register_flavor / app_flavor IS NULL
    //      的历史数据（阶段 2 / 阶段 4 波 2 之前的记录）。前端可直接 Object.entries 渲染。

    /** 用户按注册 flavor 分组：key = CN/GLOBAL/UNKNOWN, value = 用户数 */
    private Map<String, Long> usersByFlavor;

    /** 已支付订单按 flavor 分组：key = CN/GLOBAL/UNKNOWN, value = 订单数（status=PAID） */
    private Map<String, Long> paidOrdersByFlavor;

    /**
     * GMV（分）按 flavor 分组：key = CN/GLOBAL/UNKNOWN, value = 累计 amount_cents。
     * <p><b>注意</b>：跨币种（CNY / USD 等）未做 FX 换算，前端需按 flavor 分别展示单位：
     * CN → ￥（分/100=元）、GLOBAL → $（cents/100=USD）。
     */
    private Map<String, Long> gmvCentsByFlavor;

    /** 服务器时间（毫秒），便于前端校时 */
    private long serverTime;
}
