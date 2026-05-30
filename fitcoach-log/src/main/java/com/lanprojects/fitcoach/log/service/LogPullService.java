package com.lanprojects.fitcoach.log.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.BatchOperationResult;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.log.config.LogProperties;
import com.lanprojects.fitcoach.log.dto.CreateLogTaskRequest;
import com.lanprojects.fitcoach.log.dto.LogTaskDto;
import com.lanprojects.fitcoach.log.dto.PendingTaskDto;
import com.lanprojects.fitcoach.log.dto.UploadLogResponse;
import com.lanprojects.fitcoach.log.entity.LogPullStatus;
import com.lanprojects.fitcoach.log.entity.LogPullTask;
import com.lanprojects.fitcoach.log.repository.LogPullTaskRepository;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 日志拉取核心业务服务。
 *
 * <p>三个核心动作：
 * <ol>
 *   <li>{@link #createTask}：admin 端创建任务（含 24h 内同 uid 去重 + 用户存在校验）</li>
 *   <li>{@link #claimNextPending}：客户端 GET pending —— <b>同事务</b> 用 PESSIMISTIC_WRITE 锁取
 *       PENDING → 改 UPLOADING → 写 assignedAt，避免多设备并发</li>
 *   <li>{@link #acceptUpload}：客户端 POST upload —— 幂等：UPLOADED 直接返成功，UPLOADING 落盘流转，
 *       其他状态拒收</li>
 * </ol>
 *
 * <p>状态流转细节见 {@link LogPullStatus} 注释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogPullService {

    /** 失败原因落库的最大长度（与 entity 列长度对齐） */
    private static final int MAX_FAIL_REASON_LEN = 512;
    private static final int MAX_PAGE_SIZE = 200;

    private final LogPullTaskRepository taskRepository;
    private final LogStorageService storageService;
    private final UserRepository userRepository;
    private final LogProperties logProperties;

    // ==============================================================
    // admin 侧：创建 / 列表 / 详情 / 删除
    // ==============================================================

    /**
     * admin 创建一条 PENDING 任务。
     *
     * <ul>
     *   <li>校验目标 uid 存在；</li>
     *   <li>24h 内同 uid 已有未完成（PENDING/UPLOADING）任务则拒绝（避免短时间反复创建堆积）；</li>
     *   <li>expireAt = createdAt + {@code log-pull.pendingExpireHours}（默认 24h）。</li>
     * </ul>
     */
    @Transactional
    public LogTaskDto createTask(CreateLogTaskRequest req, String operator) {
        if (req == null || req.getUid() == null || req.getUid().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        String uid = req.getUid().trim();

        // 1) 用户存在性校验 — 任务面向真实用户，避免误传 uid 导致永远拉不下来
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.LOG_TASK_TARGET_USER_NOT_FOUND));
        // 注：不强制 enabled — 即使用户被禁用，admin 仍可能想拉它的历史日志排查问题

        // 2) 24h 去重
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long active = taskRepository.countActiveByUidSince(uid, since);
        if (active >= logProperties.getMaxActiveTaskPerUidIn24h()) {
            log.warn("admin 创建日志任务被去重拒绝, operator={}, uid={}, active={}",
                    operator, uid, active);
            throw new BusinessException(ResultCode.LOG_TASK_DUPLICATE_PENDING);
        }

        // 3) 落库
        LogPullTask task = new LogPullTask();
        task.setUid(uid);
        task.setStatus(LogPullStatus.PENDING);
        task.setCreatedBy(operator == null || operator.isBlank() ? "system" : operator);
        task.setRemark(truncate(req.getRemark(), 256));
        task.setRecentHours(req.getRecentHours());
        task.setExpireAt(LocalDateTime.now().plusHours(logProperties.getPendingExpireHours()));
        LogPullTask saved = taskRepository.save(task);
        log.info("admin 创建日志任务成功, operator={}, uid={}, taskId={}, recentHours={}, expireAt={}",
                operator, uid, saved.getId(), saved.getRecentHours(), saved.getExpireAt());
        return toDto(saved);
    }

    /**
     * admin 分页查询任务（按 uid 必填 / status 可选）。
     */
    public org.springframework.data.domain.Page<LogPullTask> listByUid(String uid, String statusRaw,
                                                                       int page, int size) {
        if (uid == null || uid.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1) - 1;
        var pageable = PageRequest.of(safePage, safeSize);
        if (statusRaw == null || statusRaw.isBlank()) {
            return taskRepository.findByUidOrderByCreatedAtDesc(uid.trim(), pageable);
        }
        LogPullStatus status;
        try {
            status = LogPullStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.LOG_TASK_STATUS_INVALID, "status=" + statusRaw);
        }
        return taskRepository.findByUidAndStatusOrderByCreatedAtDesc(uid.trim(), status, pageable);
    }

    /** admin 取详情（不含下载 URL，下载 URL 由 controller 拼接） */
    public LogPullTask getTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.LOG_TASK_NOT_FOUND));
    }

    /**
     * admin 删除任务（同步删盘 zip）。
     * <p>已 UPLOADED 的会先调 storage.deleteIfExists；任务行直接物理删除（DB 行少，不软删）。
     */
    @Transactional
    public void deleteTask(Long id, String operator) {
        LogPullTask task = getTaskOrThrow(id);
        if (task.getFileRelativePath() != null) {
            storageService.deleteIfExists(task.getFileRelativePath());
        }
        taskRepository.delete(task);
        log.info("admin 删除日志任务, operator={}, taskId={}, uid={}, status={}",
                operator, id, task.getUid(), task.getStatus());
    }

    /**
     * admin 批量删除任务（同步删盘 zip）。
     * <p>语义：
     * <ul>
     *   <li>ids 去重后按 DB 实际存在的部分处理；不存在的 id 收集到 missing 返回；</li>
     *   <li>整体单事务：要么本批全部成功（含删盘），要么回滚（出现底层异常时）；</li>
     *   <li>单次最多 200 条，与 admin 列表单页上限保持一致；</li>
     *   <li>对已 UPLOADED 的任务先 deleteIfExists 删盘（IO 异常会让事务回滚）；</li>
     *   <li>任务行物理删除（不软删，DB 行量小）。</li>
     * </ul>
     */
    @Transactional
    public BatchOperationResult deleteTasks(List<Long> rawIds, String operator) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id != null) uniqueIds.add(id);
        }
        if (uniqueIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ids 不能全部为 null");
        }
        if (uniqueIds.size() > 200) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作单次最多 200 条");
        }
        List<LogPullTask> rows = taskRepository.findAllById(uniqueIds);
        for (LogPullTask task : rows) {
            if (task.getFileRelativePath() != null) {
                storageService.deleteIfExists(task.getFileRelativePath());
            }
        }
        if (!rows.isEmpty()) {
            taskRepository.deleteAll(rows);
        }
        Set<Long> existingIds = new HashSet<>(rows.size());
        for (LogPullTask t : rows) existingIds.add(t.getId());
        List<Long> missing = new ArrayList<>();
        for (Long id : uniqueIds) {
            if (!existingIds.contains(id)) missing.add(id);
        }
        log.info("admin 批量删除日志任务, operator={}, requested={}, affected={}, missing={}",
                operator, uniqueIds.size(), rows.size(), missing.size());
        return BatchOperationResult.of(rows.size(), missing);
    }

    // ==============================================================
    // 客户端侧：领取 pending / 上传 / 主动报失败
    // ==============================================================

    /**
     * 客户端通用轮询入口（GET /api/client/poll → LogPullContribution）调用：
     * 取一条 PENDING 任务并原子改为 UPLOADING。
     *
     * <p>关键：必须在事务内调用 lockTopPendingForUid（PESSIMISTIC_WRITE），同时把状态/assignedAt 落库后才返回。
     * 这样多设备并发同 uid 时只会有一个拿到任务，其他设备拿到 Optional.empty()。
     *
     * @return 命中返回 PendingTaskDto；未命中（无 PENDING）返回 Optional.empty()
     */
    @Transactional
    public Optional<PendingTaskDto> claimNextPending(String uid) {
        Optional<LogPullTask> opt = taskRepository.lockTopPendingForUid(uid);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        LogPullTask task = opt.get();
        // 兜底校验 — 锁拿到的还应该是 PENDING；如果不是，说明上一秒被其他事务改了，跳过
        if (task.getStatus() != LogPullStatus.PENDING) {
            log.warn("行锁取到的任务状态非 PENDING，跳过, taskId={}, status={}", task.getId(), task.getStatus());
            return Optional.empty();
        }
        // 兜底校验 — 已过期的不再下发，让 scheduler 标 EXPIRED
        if (task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("行锁取到的任务已过期，跳过, taskId={}, expireAt={}", task.getId(), task.getExpireAt());
            return Optional.empty();
        }
        task.setStatus(LogPullStatus.UPLOADING);
        task.setAssignedAt(LocalDateTime.now());
        // assignedAt 之后 5 分钟为 UPLOADING 超时阈值，告诉客户端尽量在此前完成
        long uploadingDeadlineMs = task.getAssignedAt()
                .plusMinutes(logProperties.getUploadingTimeoutMinutes())
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        taskRepository.save(task);
        log.info("客户端领取日志任务, uid={}, taskId={}, deadlineMs={}", uid, task.getId(), uploadingDeadlineMs);
        return Optional.of(PendingTaskDto.from(task, uploadingDeadlineMs));
    }

    /**
     * 客户端 POST /api/logs/upload —— 接收 zip。
     *
     * <p>幂等规则：
     * <ul>
     *   <li>{@code task.uid != callerUid} → 7314 拒绝</li>
     *   <li>{@code task.status == UPLOADED} → 直接返成功（{@code idempotent=true}），客户端可清队列</li>
     *   <li>{@code task.status == UPLOADING} → 写盘 + 状态改 UPLOADED + 落 fileRelativePath/fileSizeBytes</li>
     *   <li>其他状态（PENDING/FAILED/EXPIRED）→ 7315 拒绝</li>
     * </ul>
     */
    @Transactional
    public UploadLogResponse acceptUpload(String callerUid, Long taskId, MultipartFile file) {
        // 入参校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.LOG_UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > logProperties.getMaxUploadSizeBytes()) {
            log.warn("日志上传超大, uid={}, taskId={}, size={}, max={}",
                    callerUid, taskId, file.getSize(), logProperties.getMaxUploadSizeBytes());
            throw new BusinessException(ResultCode.LOG_UPLOAD_FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        // 兼容 application/zip / application/x-zip-compressed / application/octet-stream
        // RN 上传同一份 zip 在不同环境下 contentType 不一致，这里宽松一些，依赖业务侧的 zip 校验后置
        if (contentType != null
                && !contentType.toLowerCase(Locale.ROOT).contains("zip")
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            log.warn("日志上传 contentType 不合法, uid={}, taskId={}, contentType={}",
                    callerUid, taskId, contentType);
            throw new BusinessException(ResultCode.LOG_UPLOAD_CONTENT_TYPE_INVALID);
        }

        LogPullTask task = getTaskOrThrow(taskId);
        if (!task.getUid().equals(callerUid)) {
            log.warn("日志上传 uid 不匹配, callerUid={}, taskUid={}, taskId={}",
                    callerUid, task.getUid(), taskId);
            throw new BusinessException(ResultCode.LOG_UPLOAD_TASK_OWNER_MISMATCH);
        }
        // 幂等：UPLOADED 直接返成功
        if (task.getStatus() == LogPullStatus.UPLOADED) {
            log.info("日志上传幂等命中（已 UPLOADED）, uid={}, taskId={}", callerUid, taskId);
            return new UploadLogResponse(taskId, task.getFileSizeBytes() == null ? 0L : task.getFileSizeBytes(), true);
        }
        // 仅 UPLOADING 才允许写盘
        if (task.getStatus() != LogPullStatus.UPLOADING) {
            log.warn("日志上传被拒：状态非 UPLOADING, uid={}, taskId={}, status={}",
                    callerUid, taskId, task.getStatus());
            throw new BusinessException(ResultCode.LOG_UPLOAD_TASK_STATUS_NOT_UPLOADING);
        }

        // 写盘 → 流转 UPLOADED
        String relativePath = storageService.saveLogZip(callerUid, taskId, file);
        task.setFileRelativePath(relativePath);
        task.setFileSizeBytes(file.getSize());
        task.setStatus(LogPullStatus.UPLOADED);
        task.setUploadedAt(LocalDateTime.now());
        // 上传成功 → 清掉之前可能写过的 failReason，避免误导后台
        task.setFailReason(null);
        taskRepository.save(task);
        log.info("日志上传完成, uid={}, taskId={}, sizeB={}", callerUid, taskId, file.getSize());
        return new UploadLogResponse(taskId, file.getSize(), false);
    }

    /**
     * 客户端 POST /api/logs/tasks/{id}/fail —— 主动回报上传失败。
     *
     * <p>状态机：
     * <ul>
     *   <li>{@code retryCount + 1 >= maxRetryCount} → FAILED；</li>
     *   <li>否则回滚 PENDING，等下一轮 pull 再领取（同时 retryCount + 1）。</li>
     * </ul>
     * <p>调用方 uid 必须与任务 uid 一致；状态非 UPLOADING 一律忽略（返回 200 不抛错，避免客户端反复重试）。
     */
    @Transactional
    public LogTaskDto reportFailure(String callerUid, Long taskId, String reason) {
        LogPullTask task = getTaskOrThrow(taskId);
        if (!task.getUid().equals(callerUid)) {
            throw new BusinessException(ResultCode.LOG_UPLOAD_TASK_OWNER_MISMATCH);
        }
        if (task.getStatus() != LogPullStatus.UPLOADING) {
            log.info("客户端回报失败但任务状态非 UPLOADING（已被 scheduler 处理），忽略, taskId={}, status={}",
                    taskId, task.getStatus());
            return toDto(task);
        }
        task.setRetryCount(task.getRetryCount() + 1);
        task.setFailReason(truncate(reason == null ? "client_reported" : reason, MAX_FAIL_REASON_LEN));
        if (task.getRetryCount() >= logProperties.getMaxRetryCount()) {
            task.setStatus(LogPullStatus.FAILED);
            log.warn("日志任务重试耗尽，标 FAILED, taskId={}, uid={}, retryCount={}",
                    taskId, callerUid, task.getRetryCount());
        } else {
            // 回滚 PENDING；assignedAt 清掉避免被 scheduler 重复回滚
            task.setStatus(LogPullStatus.PENDING);
            task.setAssignedAt(null);
            log.info("日志任务回滚 PENDING 等下次重试, taskId={}, uid={}, retryCount={}",
                    taskId, callerUid, task.getRetryCount());
        }
        taskRepository.save(task);
        return toDto(task);
    }

    // ==============================================================
    // 工具
    // ==============================================================

    public LogTaskDto toDto(LogPullTask task) {
        return LogTaskDto.from(task, null);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
