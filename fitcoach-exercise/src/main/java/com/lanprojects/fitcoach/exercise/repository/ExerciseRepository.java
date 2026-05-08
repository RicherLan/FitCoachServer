package com.lanprojects.fitcoach.exercise.repository;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.entity.MuscleGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * @param muscleGroup 肌群分类
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
    long countOtherFreeInGroup(MuscleGroup muscleGroup, Long excludeId);
}
