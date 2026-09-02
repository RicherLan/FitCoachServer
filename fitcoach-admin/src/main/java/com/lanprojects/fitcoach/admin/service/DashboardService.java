package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.DashboardOverviewDto;
import com.lanprojects.fitcoach.common.cache.CacheNames;
import com.lanprojects.fitcoach.common.client.AppFlavor;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
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
    private final PaymentOrderRepository paymentOrderRepository;

    /** 阶段 6 波 1 flavor 分组 key 常量：Server / Admin / SDK 保持一致 */
    private static final String FLAVOR_KEY_CN = "CN";
    private static final String FLAVOR_KEY_GLOBAL = "GLOBAL";
    private static final String FLAVOR_KEY_UNKNOWN = "UNKNOWN";

    /** 拉取概览数据（用户 + 反馈 + 阶段 6 波 1 flavor 分组）— Caffeine 缓存 30s */
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

        // ==================== 阶段 6 波 1 flavor 分组 ====================
        // 3 个 Map 都固定 CN → GLOBAL → UNKNOWN 顺序，前端 Object.entries 遍历渲染即可。
        // 用户 & 已支付订单 & GMV 三条线各 3 次查库，总共 9 个 SQL；缓存 30s 期间共享，压力可控。
        Map<String, Long> usersByFlavor = new LinkedHashMap<>();
        usersByFlavor.put(FLAVOR_KEY_CN, userRepository.countByRegisterFlavor(AppFlavor.CN));
        usersByFlavor.put(FLAVOR_KEY_GLOBAL, userRepository.countByRegisterFlavor(AppFlavor.GLOBAL));
        usersByFlavor.put(FLAVOR_KEY_UNKNOWN, userRepository.countByRegisterFlavorIsNull());

        Map<String, Long> paidOrdersByFlavor = new LinkedHashMap<>();
        paidOrdersByFlavor.put(FLAVOR_KEY_CN,
                paymentOrderRepository.countByStatusAndAppFlavor(OrderStatus.PAID, AppFlavor.CN));
        paidOrdersByFlavor.put(FLAVOR_KEY_GLOBAL,
                paymentOrderRepository.countByStatusAndAppFlavor(OrderStatus.PAID, AppFlavor.GLOBAL));
        paidOrdersByFlavor.put(FLAVOR_KEY_UNKNOWN,
                paymentOrderRepository.countByStatusAndAppFlavorIsNull(OrderStatus.PAID));

        Map<String, Long> gmvCentsByFlavor = new LinkedHashMap<>();
        gmvCentsByFlavor.put(FLAVOR_KEY_CN,
                paymentOrderRepository.sumAmountCentsByStatusAndAppFlavor(OrderStatus.PAID, AppFlavor.CN));
        gmvCentsByFlavor.put(FLAVOR_KEY_GLOBAL,
                paymentOrderRepository.sumAmountCentsByStatusAndAppFlavor(OrderStatus.PAID, AppFlavor.GLOBAL));
        gmvCentsByFlavor.put(FLAVOR_KEY_UNKNOWN,
                paymentOrderRepository.sumAmountCentsByStatusAndAppFlavorIsNull(OrderStatus.PAID));

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
                .usersByFlavor(usersByFlavor)
                .paidOrdersByFlavor(paidOrdersByFlavor)
                .gmvCentsByFlavor(gmvCentsByFlavor)
                .serverTime(System.currentTimeMillis())
                .build();
    }
}
