package com.lanprojects.fitcoach.exercise.repository;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findByExerciseKey(String exerciseKey);

    /** 客户端列表：只返回启用的，按 sortOrder 升序 */
    List<Exercise> findByEnabledTrueOrderBySortOrderAsc();

    /** Admin 列表：返回所有（含禁用），按 sortOrder 升序 */
    List<Exercise> findAllByOrderBySortOrderAsc();

    /**
     * 校验"该肌群是否还有其他启用且免费的动作"——用于 admin 修改 isFree=false 或 enabled=false 时的保护规则。
     *
     * @param muscleGroup 肌群 groupKey 字符串（如 "CHEST"）
     * @param excludeId   排除的动作 id（即正在被修改的那条）
     * @return 该肌群下除 excludeId 外，还有多少个 enabled=true && isFree=true 的动作
     */
    @Query("""
            SELECT COUNT(e) FROM Exercise e
            WHERE e.muscleGroup = :muscleGroup
              AND e.enabled = true
              AND e.isFree = true
              AND e.id <> :excludeId
            """)
    long countOtherFreeInGroup(@Param("muscleGroup") String muscleGroup, @Param("excludeId") Long excludeId);

    /**
     * 按肌群 key 字符串统计 Exercise 数量（用于 MuscleGroupService 删除前检查）。
     *
     * <p><b>故意走 native query</b>：muscle_group 列底层是 VARCHAR，
     * S3 之前 Exercise.muscleGroup 是 enum（{@code @Enumerated(EnumType.STRING)} 写库为字符串），
     * S3 之后该字段会改为 String。两种情况下底层列值都是字符串，原生 SQL 都能正确匹配，
     * 避免迁移过程中需要修改本方法签名。
     */
    @Query(value = "SELECT COUNT(*) FROM exercise WHERE muscle_group = :groupKey", nativeQuery = true)
    long countByMuscleGroupKey(@Param("groupKey") String groupKey);
}
