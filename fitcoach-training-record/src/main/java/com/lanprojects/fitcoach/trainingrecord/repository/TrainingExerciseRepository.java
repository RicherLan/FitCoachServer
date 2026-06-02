package com.lanprojects.fitcoach.trainingrecord.repository;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * TrainingExercise 仓储。
 *
 * <p><b>关键查询设计</b>：
 * <ul>
 *   <li>"内置 + 当前用户自定义" 合并查询用 {@link #findVisibleEnabledForUser(Long)}；</li>
 *   <li>Seeder 幂等检查走 {@link #findByExerciseKeyAndUserIdIsNull(String)}（内置 key 全局唯一）。</li>
 * </ul>
 */
public interface TrainingExerciseRepository extends JpaRepository<TrainingExercise, Long> {

    /**
     * 按 key 查找内置动作（userId IS NULL）。
     * <p>Seeder / admin CRUD 用。
     */
    Optional<TrainingExercise> findByExerciseKeyAndUserIdIsNull(String exerciseKey);

    /**
     * 按 key + 用户查找（自定义动作专用）。
     * <p>MVP 暂未开放，但接口保留以备 P1 用。
     */
    Optional<TrainingExercise> findByExerciseKeyAndUserId(String exerciseKey, Long userId);

    /**
     * 用户端列表：内置（user_id IS NULL）+ 自己自定义（user_id = :userId），仅启用，按 sortOrder 升序。
     *
     * <p>{@link #findVisibleEnabledForUser(Long)} 是用户端核心查询接口：用户在记录页选动作时，
     * 既能看到 admin 维护的 86+ 内置动作，也能看到自己创建的私人动作；其他用户的自定义不可见。
     */
    @Query("""
            SELECT t FROM TrainingExercise t
            WHERE t.enabled = true
              AND (t.userId IS NULL OR t.userId = :userId)
            ORDER BY t.sortOrder ASC, t.id ASC
            """)
    List<TrainingExercise> findVisibleEnabledForUser(@Param("userId") Long userId);

    /**
     * Admin 列表：全部内置动作（user_id IS NULL，含禁用），按 sortOrder 升序。
     * <p>Admin 后台不展示用户自定义动作（自定义不归 admin 管理面板）。
     */
    @Query("""
            SELECT t FROM TrainingExercise t
            WHERE t.userId IS NULL
            ORDER BY t.sortOrder ASC, t.id ASC
            """)
    List<TrainingExercise> findAllBuiltinOrderBySortOrder();

    /**
     * 按肌群 key 统计内置动作数量（admin 删除肌群前的校验用，与 Exercise.countByMuscleGroupKey 配合）。
     */
    @Query(value = "SELECT COUNT(*) FROM training_exercise WHERE muscle_group = :groupKey AND user_id IS NULL",
            nativeQuery = true)
    long countBuiltinByMuscleGroupKey(@Param("groupKey") String groupKey);
}
