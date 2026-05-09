package com.lanprojects.fitcoach.exercise.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import com.lanprojects.fitcoach.exercise.repository.ExerciseRepository;
import com.lanprojects.fitcoach.exercise.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 肌群核心服务（用户端列表 + admin CRUD 共用）。
 *
 * <p>本服务**只关心肌群元数据**，不关心 Exercise/会员校验：
 * <ul>
 *   <li>delete：硬保护——若该肌群下还有 Exercise 引用，禁止删除（返回 7603）；</li>
 *   <li>disable（enabled=false）：弱保护——允许，由"客户端首页空类目自动隐藏"逻辑兜底，
 *       这样运营可以快速下线一个肌群（含其下所有动作的展示），不需要先迁移动作。</li>
 * </ul>
 *
 * <p>"每个肌群至少 1 个免费动作"规则仍由 {@link ExerciseService} 强制（保留原有行为不变）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MuscleGroupService {

    private final MuscleGroupRepository muscleGroupRepository;
    private final ExerciseRepository exerciseRepository;

    // ====== 查询 ======

    /** 客户端列表：只返回启用的（按 sortOrder 升序） */
    public List<MuscleGroupEntity> listEnabled() {
        return muscleGroupRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    /** Admin 列表：返回所有（含禁用，按 sortOrder 升序） */
    public List<MuscleGroupEntity> listAll() {
        return muscleGroupRepository.findAllByOrderBySortOrderAsc();
    }

    public MuscleGroupEntity findByKey(String groupKey) {
        return muscleGroupRepository.findByGroupKey(groupKey)
                .orElseThrow(() -> new BusinessException(ResultCode.MUSCLE_GROUP_NOT_FOUND));
    }

    public MuscleGroupEntity findById(Long id) {
        return muscleGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.MUSCLE_GROUP_NOT_FOUND));
    }

    public boolean existsByKey(String groupKey) {
        return muscleGroupRepository.existsByGroupKey(groupKey);
    }

    // ====== Admin 写操作 ======

    /** 创建新肌群。会校验 groupKey 唯一性。 */
    public MuscleGroupEntity create(MuscleGroupEntity toCreate) {
        if (muscleGroupRepository.existsByGroupKey(toCreate.getGroupKey())) {
            throw new BusinessException(ResultCode.MUSCLE_GROUP_KEY_DUPLICATE);
        }
        toCreate.setId(null); // 强制走数据库自增
        MuscleGroupEntity saved = muscleGroupRepository.save(toCreate);
        log.info("[muscle-group] 创建肌群 id={} key={} displayName={} enabled={}",
                saved.getId(), saved.getGroupKey(), saved.getDisplayName(), saved.getEnabled());
        return saved;
    }

    /**
     * 更新（PATCH 语义：null = 不动）。
     * <p><b>groupKey 不允许更新</b>（一旦发布，已有 Exercise 引用，改名会破坏关联）。
     */
    public MuscleGroupEntity update(Long id, MuscleGroupEntity patch) {
        MuscleGroupEntity existing = findById(id);

        // groupKey 不允许更新（保护 Exercise 引用）；patch 的 groupKey 一律忽略
        if (patch.getDisplayName() != null) existing.setDisplayName(patch.getDisplayName());
        if (patch.getEmoji() != null) existing.setEmoji(patch.getEmoji());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getColor() != null) existing.setColor(patch.getColor());
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());

        MuscleGroupEntity saved = muscleGroupRepository.save(existing);
        log.info("[muscle-group] 更新肌群 id={} key={} enabled={} sortOrder={}",
                saved.getId(), saved.getGroupKey(), saved.getEnabled(), saved.getSortOrder());
        return saved;
    }

    /**
     * 硬删除——若该肌群下还有 Exercise 引用，抛 7603。
     * <p>常规运营建议用 {@code enabled=false} 软下架；删除仅用于"该 key 完全不要了"的场景。
     */
    public void delete(Long id) {
        MuscleGroupEntity existing = findById(id);
        long refCount = exerciseRepository.countByMuscleGroupKey(existing.getGroupKey());
        if (refCount > 0) {
            log.warn("[muscle-group] 拒绝删除肌群 id={} key={}，仍有 {} 个 Exercise 引用",
                    id, existing.getGroupKey(), refCount);
            throw new BusinessException(ResultCode.MUSCLE_GROUP_HAS_EXERCISES);
        }
        muscleGroupRepository.delete(existing);
        log.info("[muscle-group] 删除肌群 id={} key={}", id, existing.getGroupKey());
    }
}
