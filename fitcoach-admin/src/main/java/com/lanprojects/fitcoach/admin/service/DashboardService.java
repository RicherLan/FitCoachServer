package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.DashboardOverviewDto;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>用 Redis cache 缓存概览数据 + 定时刷新；</li>
 *   <li>把"近 N 天新增"切到独立的 daily_user_stat 表预聚合。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final UserFeedbackRepository userFeedbackRepository;

    /** 拉取概览数据（用户 + 反馈） */
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
