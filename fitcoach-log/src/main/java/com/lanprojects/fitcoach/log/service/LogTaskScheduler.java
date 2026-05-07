package com.lanprojects.fitcoach.log.service;

import com.lanprojects.fitcoach.log.config.LogProperties;
import com.lanprojects.fitcoach.log.entity.LogPullStatus;
import com.lanprojects.fitcoach.log.entity.LogPullTask;
import com.lanprojects.fitcoach.log.repository.LogPullTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志任务后台维护调度器。
 *
 * <p>三类批扫，统一周期（默认 60s，可由 {@code log-pull.scheduler-interval-seconds} 调整）：
 * <ol>
 *   <li>{@link #scanStaleUploading} —— UPLOADING 超过 5 分钟未完成 → 回滚 PENDING（retryCount + 1，达到上限标 FAILED）；</li>
 *   <li>{@link #scanExpiredPending} —— PENDING 超过 expireAt（默认创建后 24h）→ 标 EXPIRED；</li>
 *   <li>{@link #scanStaleUploaded} —— UPLOADED 超过 7 天 → 删盘 + 标 EXPIRED（保留任务行作为审计）。</li>
 * </ol>
 *
 * <p>用 {@code @Scheduled(fixedDelayString=...)} 而非 cron，避免上一轮还没跑完就触发下一轮（fixedDelay
 * 是上一次完成 → 等待 → 再开始）。
 *
 * <p>注意：本调度器不加分布式锁；当前部署是单实例，多实例部署需切换为带 ShedLock 的方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogTaskScheduler {

    private final LogPullTaskRepository taskRepository;
    private final LogStorageService storageService;
    private final LogProperties logProperties;

    /**
     * 扫超时 UPLOADING：assignedAt + uploadingTimeoutMinutes 已过仍未上传完。
     *
     * <p>处理：retryCount+1；超上限 → FAILED，否则 → PENDING（清 assignedAt）。
     */
    @Scheduled(fixedDelayString = "${log-pull.scheduler-interval-seconds:60}000")
    @Transactional
    public void scanStaleUploading() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusMinutes(logProperties.getUploadingTimeoutMinutes());
        List<LogPullTask> stale = taskRepository.findStaleUploading(deadline);
        if (stale.isEmpty()) return;

        int rollback = 0, failed = 0;
        for (LogPullTask t : stale) {
            t.setRetryCount(t.getRetryCount() + 1);
            t.setFailReason(truncate("uploading_timeout(" + logProperties.getUploadingTimeoutMinutes() + "m)", 512));
            if (t.getRetryCount() >= logProperties.getMaxRetryCount()) {
                t.setStatus(LogPullStatus.FAILED);
                failed++;
            } else {
                t.setStatus(LogPullStatus.PENDING);
                t.setAssignedAt(null);
                rollback++;
            }
            taskRepository.save(t);
        }
        log.warn("[LogScheduler] UPLOADING 超时扫描完成, 共 {} 条，回滚 PENDING={}, 标 FAILED={}",
                stale.size(), rollback, failed);
    }

    /**
     * 扫过期 PENDING：超过 expireAt（默认创建后 24h）→ EXPIRED。
     */
    @Scheduled(fixedDelayString = "${log-pull.scheduler-interval-seconds:60}000")
    @Transactional
    public void scanExpiredPending() {
        List<LogPullTask> expired = taskRepository.findExpiredPending(LocalDateTime.now());
        if (expired.isEmpty()) return;
        for (LogPullTask t : expired) {
            t.setStatus(LogPullStatus.EXPIRED);
            t.setFailReason(truncate("pending_expired(" + logProperties.getPendingExpireHours() + "h)", 512));
            taskRepository.save(t);
        }
        log.info("[LogScheduler] PENDING 过期扫描完成, 标 EXPIRED 数={}", expired.size());
    }

    /**
     * 扫过期 UPLOADED：uploadedAt + retentionDays 已过 → 删盘 + 标 EXPIRED。
     *
     * <p>保留任务行不删，便于追溯哪条任务被清掉了。
     */
    @Scheduled(fixedDelayString = "${log-pull.scheduler-interval-seconds:60}000")
    @Transactional
    public void scanStaleUploaded() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusDays(logProperties.getUploadedRetentionDays());
        List<LogPullTask> stale = taskRepository.findStaleUploaded(deadline);
        if (stale.isEmpty()) return;
        for (LogPullTask t : stale) {
            // 先删盘，再改状态；删盘失败不抛（storageService 内部 swallow），避免一条卡死后续任务
            if (t.getFileRelativePath() != null) {
                storageService.deleteIfExists(t.getFileRelativePath());
            }
            t.setStatus(LogPullStatus.EXPIRED);
            t.setFailReason(truncate("uploaded_retention_expired(" + logProperties.getUploadedRetentionDays() + "d)", 512));
            taskRepository.save(t);
        }
        log.info("[LogScheduler] UPLOADED 文件清理完成, 数={}", stale.size());
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
