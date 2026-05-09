package com.lanprojects.fitcoach.exercise.repository;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MuscleGroupRepository extends JpaRepository<MuscleGroupEntity, Long> {

    Optional<MuscleGroupEntity> findByGroupKey(String groupKey);

    /** 客户端列表：只返回启用的，按 sortOrder 升序 */
    List<MuscleGroupEntity> findByEnabledTrueOrderBySortOrderAsc();

    /** Admin 列表：返回所有（含禁用），按 sortOrder 升序 */
    List<MuscleGroupEntity> findAllByOrderBySortOrderAsc();

    boolean existsByGroupKey(String groupKey);
}
