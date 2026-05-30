package com.lanprojects.fitcoach.membership.job;

import com.lanprojects.fitcoach.membership.entity.MembershipActivationFailure;
import com.lanprojects.fitcoach.membership.repository.MembershipActivationFailureRepository;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员激活失败重试任务 — 周期扫描 {@code membership_activation_failure} 表中 PENDING 且
 * {@code next_retry_at <= now} 的记录，调用 {@link MembershipService#retryActivation} 尝试重新激活。
 *
 * <p><b>与 P0-2 的 MembershipReconcileJob 的分工</b>：
 * <ul>
 *   <li>{@code MembershipReconcileJob}（fitcoach-app 内）：扫 PAID 订单，反查会员是否被激活，
 *       兜底 "事件根本没投递" 的极端场景；</li>
 *   <li>本 retry job（fitcoach-membership 内）：扫显式的失败记录表，做指数退避重试，
 *       覆盖 "事件投递了但激活逻辑抛异常" 的常见场景。</li>
 * </ul>
 * 两者互为冗余，是双保险。
 *
 * <p><b>调度策略</b>：固定频率 1 分钟一次（足够细，但失败上限 10 次 + 指数退避后总时长 ≈ 32h，
 * 单条记录不会一直占用 CPU），单次最多取 50 条防止单实例打满。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipActivationRetryJob {

    /** 单次扫描最多处理的条数 — 防止失败记录暴涨时单实例打满 */
    private static final int BATCH_SIZE = 50;

    private final MembershipActivationFailureRepository repository;
    private final MembershipService membershipService;

    @Scheduled(
            fixedDelayString = "${membership.activation-retry.interval-ms:60000}",
            initialDelayString = "${membership.activation-retry.initial-delay-ms:30000}"
    )
    public void runRetry() {
        try {
            List<MembershipActivationFailure> batch = repository.findReadyForRetry(LocalDateTime.now(), BATCH_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            log.info("[membership-retry] 开始重试激活 batchSize={}", batch.size());
            int success = 0;
            int failed = 0;
            for (MembershipActivationFailure failure : batch) {
                try {
                    membershipService.retryActivation(failure);
                    // 重试结果在 MembershipService 内部更新状态 — 这里只汇总日志
                    // 重读最新状态判断
                    repository.findById(failure.getId()).ifPresent(latest -> {
                        // 仅做日志用
                    });
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.error("[membership-retry] 重试任务异常 orderId={}",
                            failure.getOrderId(), e);
                }
            }
            log.info("[membership-retry] 重试完成 attempted={} succeededOrUpdated={} jobError={}",
                    batch.size(), success, failed);
        } catch (Exception e) {
            // job 自身异常绝对不能扩散，否则 Spring scheduler 会丢调度
            log.error("[membership-retry] 任务扫描异常", e);
        }
    }
}
