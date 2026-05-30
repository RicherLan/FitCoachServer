package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.DashboardOverviewDto;
import com.lanprojects.fitcoach.common.cache.CacheNames;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard 概览统计服务。
 * <p>
 * 当前实现走 SQL count 聚合（数据量小阶段足够），后续如果用户量上来，可以：
 * <ul>
 *   <li>把"近 N 天新增"切到独立的 daily_user_stat 表预聚合；</li>
 *   <li>切到 Redis 缓存（只需替换 {@link com.lanprojects.fitcoach.common.cache.CacheConfig} 的 CacheManager Bean）。</li>
 * </ul>
 *
 * <p><b>P2-4 缓存</b>：每次 {@link #getOverview()} 触发 10+ count 查询。
 * Caffeine 缓存 30 秒：多个 admin 同时打开首页时只查一次库，且 30 秒延迟对趋势数据无感。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final UserFeedbackRepository userFeedbackRepository;

    /** 拉取概览数据（用户 + 反馈）— Caffeine 缓存 30s */
    @Cacheable(value = CacheNames.ADMIN_DASHBOARD_OVERVIEW, key = "'overview'")
    public DashboardOverviewDto getOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime sevenDaysAgo = todayStart.minusDays(6);   // 含今天共 7 天
        LocalDateTime thirtyDaysAgo = todayStart.minusDays(29); // 含今天共 30 天

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabled(true);
        long newToday = userRepository.countByCreatedAtBetween(todayStart, tomorrowStart);
        long newLast7 = userRepository.countByCreatedAtBetween(sevenDaysAgo, tomorrowStart);
        long newLast30 = userRepository.countByCreatedAtBetween(thirtyDaysAgo, tomorrowStart);

        long totalFb = userFeedbackRepository.count();
        long pending = userFeedbackRepository.countByStatus(FeedbackStatus.PENDING);
        long processing = userFeedbackRepository.countByStatus(FeedbackStatus.PROCESSING);
        long resolved = userFeedbackRepository.countByStatus(FeedbackStatus.RESOLVED);
        long ignored = userFeedbackRepository.countByStatus(FeedbackStatus.IGNORED);

        Map<String, Long> byType = new LinkedHashMap<>();
        for (FeedbackType t : FeedbackType.values()) {
            byType.put(t.name(), userFeedbackRepository.countByType(t));
        }

        return DashboardOverviewDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .newUsersToday(newToday)
                .newUsersLast7Days(newLast7)
                .newUsersLast30Days(newLast30)
                .totalFeedbacks(totalFb)
                .pendingFeedbacks(pending)
                .processingFeedbacks(processing)
                .resolvedFeedbacks(resolved)
                .ignoredFeedbacks(ignored)
                .feedbacksByType(byType)
                .serverTime(System.currentTimeMillis())
                .build();
    }
}
