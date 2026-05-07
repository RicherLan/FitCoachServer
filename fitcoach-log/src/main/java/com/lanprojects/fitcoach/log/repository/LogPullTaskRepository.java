package com.lanprojects.fitcoach.log.repository;

import com.lanprojects.fitcoach.log.entity.LogPullStatus;
import com.lanprojects.fitcoach.log.entity.LogPullTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 日志任务数据访问层。
 *
 * <p>核心设计：
 * <ul>
 *   <li>{@link #lockTopPendingForUid(String)} 用 PESSIMISTIC_WRITE 行锁，避免同一 uid 多设备并发 pull 时把同一行
 *       重复改成 UPLOADING；</li>
 *   <li>{@link #findExistingActiveByUid(String, java.time.LocalDateTime)} 用于 admin 创建任务时校验
 *       "24h 内是否已有未完成任务"，覆盖 PENDING/UPLOADING；</li>
 *   <li>scheduler 三类批扫：{@link #findStaleUploading}（5 分钟回滚）、{@link #findExpiredPending}（24h 标
 *       EXPIRED）、{@link #findStaleUploaded}（7 天清盘）。</li>
 * </ul>
 */
public interface LogPullTaskRepository
        extends JpaRepository<LogPullTask, Long>, JpaSpecificationExecutor<LogPullTask> {

    /**
     * 取该 uid 状态为 PENDING 的最早 1 条，并加排他行锁。
     * <p>必须在事务内调用（{@code @Transactional}），否则锁会立刻释放。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select t from LogPullTask t
            where t.uid = :uid
              and t.status = com.lanprojects.fitcoach.log.entity.LogPullStatus.PENDING
            order by t.createdAt asc
           """)
    List<LogPullTask> lockTopPendingForUidImpl(@Param("uid") String uid, Pageable pageable);

    /** 默认取 1 条；用 default 方法包一层 Pageable，避免调用方写 PageRequest */
    default Optional<LogPullTask> lockTopPendingForUid(String uid) {
        List<LogPullTask> list = lockTopPendingForUidImpl(uid, org.springframework.data.domain.PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 是否存在该 uid "未完成" 的任务（创建于 since 之后）。
     * <p>未完成 = PENDING 或 UPLOADING；用于 admin 创建任务时去重。
     */
    @Query("""
           select count(t) from LogPullTask t
            where t.uid = :uid
              and t.createdAt >= :since
              and t.status in (
                com.lanprojects.fitcoach.log.entity.LogPullStatus.PENDING,
                com.lanprojects.fitcoach.log.entity.LogPullStatus.UPLOADING
              )
           """)
    long countActiveByUidSince(@Param("uid") String uid, @Param("since") LocalDateTime since);

    /** scheduler 批扫：UPLOADING 且 assignedAt < deadline 的任务 */
    @Query("""
           select t from LogPullTask t
            where t.status = com.lanprojects.fitcoach.log.entity.LogPullStatus.UPLOADING
              and t.assignedAt is not null
              and t.assignedAt < :deadline
           """)
    List<LogPullTask> findStaleUploading(@Param("deadline") LocalDateTime deadline);

    /** scheduler 批扫：PENDING 但已超过 expireAt（默认创建后 24h） */
    @Query("""
           select t from LogPullTask t
            where t.status = com.lanprojects.fitcoach.log.entity.LogPullStatus.PENDING
              and t.expireAt < :now
           """)
    List<LogPullTask> findExpiredPending(@Param("now") LocalDateTime now);

    /** scheduler 批扫：UPLOADED 且 uploadedAt < deadline 的任务（用于 7 天清盘） */
    @Query("""
           select t from LogPullTask t
            where t.status = com.lanprojects.fitcoach.log.entity.LogPullStatus.UPLOADED
              and t.uploadedAt is not null
              and t.uploadedAt < :deadline
           """)
    List<LogPullTask> findStaleUploaded(@Param("deadline") LocalDateTime deadline);

    /** admin 列表查询用（status 可选） */
    Page<LogPullTask> findByUidOrderByCreatedAtDesc(String uid, Pageable pageable);

    Page<LogPullTask> findByUidAndStatusOrderByCreatedAtDesc(String uid, LogPullStatus status, Pageable pageable);

    /** scheduler 内部统计（被复用 SQL 时方便） */
    long countByStatus(LogPullStatus status);

    /** admin Dashboard 可选：某 uid 累计任务数 */
    long countByUid(String uid);

    /**
     * 状态流转的"原子改"用法：状态机推进时配合 {@code @Transactional} 直接 save，
     * 不再单独写 @Modifying SQL — JPA 在事务内对同一受管实体的字段更新会自动生成 UPDATE。
     */
    @Modifying
    @Query("""
           update LogPullTask t set t.status = :to, t.failReason = :reason
            where t.id = :id and t.status = :from
           """)
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("from") LogPullStatus from,
                            @Param("to") LogPullStatus to,
                            @Param("reason") String reason);
}
