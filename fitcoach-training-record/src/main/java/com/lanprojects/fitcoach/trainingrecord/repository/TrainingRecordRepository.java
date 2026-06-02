package com.lanprojects.fitcoach.trainingrecord.repository;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * TrainingRecord 仓储。
 *
 * <p><b>关键查询</b>：
 * <ul>
 *   <li>{@link #findByUserIdAndClientId} — 幂等的核心：客户端重试 / 离线队列重发都走这条路径；</li>
 *   <li>{@link #pageByUserOrderByDateDesc} — 用户端列表分页（按日期倒序，新的训练在前）；</li>
 *   <li>{@link #countByUserIdAndDateBetween} — 月度概要 / 统计页用：某段时间内的训练次数。</li>
 * </ul>
 *
 * <p>子表 TrainingRecordExercise / TrainingRecordSet 通过 OneToMany cascade 自动级联，
 * 没必要单独写 Repository（按 record id 查走 entity.exercises 即可，已有 @OrderBy）。
 */
public interface TrainingRecordRepository extends JpaRepository<TrainingRecord, Long> {

    /** 幂等查询：同一用户的相同 clientId 只会有一条 */
    Optional<TrainingRecord> findByUserIdAndClientId(Long userId, String clientId);

    /** 用户列表分页（按 date desc, id desc） */
    @Query("""
            SELECT r FROM TrainingRecord r
            WHERE r.userId = :userId
            ORDER BY r.date DESC, r.id DESC
            """)
    Page<TrainingRecord> pageByUserOrderByDateDesc(@Param("userId") Long userId, Pageable pageable);

    /** 取用户某天/某段时间的所有训练记录（详情/编辑/月概要用） */
    @Query("""
            SELECT r FROM TrainingRecord r
            WHERE r.userId = :userId
              AND r.date BETWEEN :start AND :end
            ORDER BY r.date DESC, r.id DESC
            """)
    List<TrainingRecord> listByUserBetween(@Param("userId") Long userId,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    /** 月度训练次数 / 统计页用 */
    long countByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    /**
     * 按 id + userId 查询（用于"只能看自己的训练记录"权限校验，避免 IDOR 漏洞）。
     * <p>找不到时调用方应抛 TRAINING_RECORD_NOT_FOUND（外部不可区分"不存在"和"非本人"，防枚举）。
     */
    Optional<TrainingRecord> findByIdAndUserId(Long id, Long userId);

    // ====== Admin 端只读查询 ======

    /** Admin 列表分页：全用户的训练记录，按 createdAt desc */
    @Query("""
            SELECT r FROM TrainingRecord r
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<TrainingRecord> pageAllForAdmin(Pageable pageable);

    /** Admin 按用户 id 过滤 */
    @Query("""
            SELECT r FROM TrainingRecord r
            WHERE r.userId = :userId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<TrainingRecord> pageByUserForAdmin(@Param("userId") Long userId, Pageable pageable);

    /** Admin 按日期范围过滤（含分页） */
    @Query("""
            SELECT r FROM TrainingRecord r
            WHERE r.date BETWEEN :start AND :end
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<TrainingRecord> pageByDateRangeForAdmin(@Param("start") LocalDate start,
                                                 @Param("end") LocalDate end,
                                                 Pageable pageable);
}
