package com.lanprojects.fitcoach.exercise.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 健身动作核心服务（用户端 + admin 端共用）。
 *
 * <p>本服务**只关心动作元数据**，不关心会员校验。会员校验由调用方（业务接口的 Controller / 守卫）
 * 在拿到 Exercise 后通过 MembershipService.requireMembership() 自行处理，避免 exercise 模块
 * 反向依赖 membership 模块（保持单向依赖：exercise 不依赖 membership 不依赖 payment）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    // ====== 查询 ======

    /** 客户端列表：只返回启用的 */
    public List<Exercise> listEnabled() {
        return exerciseRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    /** Admin 列表：返回所有（含禁用） */
    public List<Exercise> listAll() {
        return exerciseRepository.findAllByOrderBySortOrderAsc();
    }

    /**
     * 按 key 查询，找不到抛 EXERCISE_NOT_FOUND。
     * <p>注意：业务调用方拿到 Exercise 后还要自行检查 enabled —— 我们这里不强校验，
     * 因为 admin 后台编辑场景需要能拿到禁用动作。
     */
    public Exercise findByKey(String exerciseKey) {
        return exerciseRepository.findByExerciseKey(exerciseKey)
                .orElseThrow(() -> new BusinessException(ResultCode.EXERCISE_NOT_FOUND));
    }

    public Exercise findById(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.EXERCISE_NOT_FOUND));
    }

    /**
     * 业务网关：客户端调用某动作能力前用此方法。
     * <p>规则：
     * <ul>
     *   <li>动作必须启用，否则 EXERCISE_DISABLED；</li>
     *   <li>免费动作直接放行；</li>
     *   <li>付费动作不在此处校验会员（会员校验由调用方持有 MembershipService 时完成），
     *       但本方法返回的 Exercise 上的 isFree 字段已经是最新状态，调用方读它即可。</li>
     * </ul>
     */
    public Exercise findEnabledByKey(String exerciseKey) {
        Exercise exercise = findByKey(exerciseKey);
        if (Boolean.FALSE.equals(exercise.getEnabled())) {
            throw new BusinessException(ResultCode.EXERCISE_DISABLED);
        }
        return exercise;
    }

    // ====== Admin 写操作 ======

    /**
     * 创建新动作。会校验 exerciseKey 唯一性。
     */
    public Exercise create(Exercise toCreate) {
        exerciseRepository.findByExerciseKey(toCreate.getExerciseKey()).ifPresent(e -> {
            throw new BusinessException(ResultCode.EXERCISE_KEY_DUPLICATE);
        });
        // 强制 id 走数据库自增
        toCreate.setId(null);
        Exercise saved = exerciseRepository.save(toCreate);
        log.info("[exercise] 创建动作 id={} key={} muscleGroup={} isFree={}",
                saved.getId(), saved.getExerciseKey(), saved.getMuscleGroup(), saved.getIsFree());
        return saved;
    }

    /**
     * 更新动作（含 isFree / enabled），带"每个肌群至少保留一个免费动作"的保护规则。
     * <p>规则触发条件：
     * <ul>
     *   <li>新值 isFree=false（要把这个免费动作改成付费），且原值 isFree=true；</li>
     *   <li>或者 新值 enabled=false（要下架这个动作），且原值 enabled=true && isFree=true；</li>
     * </ul>
     * 满足上述任一时，必须确认该肌群下还有至少 1 个其他 enabled && isFree 的动作。
     */
    public Exercise update(Long id, Exercise patch) {
        Exercise existing = findById(id);

        // 检查保护规则：是否会让该肌群失去最后一个免费动作
        boolean willLoseLastFreeFromIsFree = Boolean.TRUE.equals(existing.getIsFree())
                && Boolean.FALSE.equals(patch.getIsFree());
        boolean willLoseLastFreeFromDisable = Boolean.TRUE.equals(existing.getEnabled())
                && Boolean.TRUE.equals(existing.getIsFree())
                && Boolean.FALSE.equals(patch.getEnabled());
        if (willLoseLastFreeFromIsFree || willLoseLastFreeFromDisable) {
            long otherFree = exerciseRepository.countOtherFreeInGroup(existing.getMuscleGroup(), id);
            if (otherFree == 0) {
                throw new BusinessException(ResultCode.EXERCISE_LAST_FREE_IN_GROUP);
            }
        }

        // 应用 patch（PATCH 语义：null = 不动；仅允许修改业务字段，不允许改 exerciseKey 和 id）
        if (patch.getDisplayName() != null) existing.setDisplayName(patch.getDisplayName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getMuscles() != null) existing.setMuscles(patch.getMuscles());
        if (patch.getEmoji() != null) existing.setEmoji(patch.getEmoji());
        if (patch.getMuscleGroup() != null) existing.setMuscleGroup(patch.getMuscleGroup());
        if (patch.getCameraSetupJson() != null) existing.setCameraSetupJson(patch.getCameraSetupJson());
        if (patch.getIsFree() != null) existing.setIsFree(patch.getIsFree());
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());

        Exercise saved = exerciseRepository.save(existing);
        log.info("[exercise] 更新动作 id={} key={} isFree={} enabled={}",
                saved.getId(), saved.getExerciseKey(), saved.getIsFree(), saved.getEnabled());
        return saved;
    }
}
