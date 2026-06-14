package com.lanprojects.fitcoach.trainingrecord.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.exercise.repository.MuscleGroupRepository;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import com.lanprojects.fitcoach.trainingrecord.repository.TrainingExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 训练动作库核心服务（用户端列表 + admin CRUD 共用）。
 *
 * <p>设计原则：本服务**只关心动作元数据**，与训练记录（TrainingRecord）解耦 —— 后者通过
 * key + 名字快照引用本表，本表的启停 / 删除不会破坏已存在的历史训练记录。
 *
 * <p><b>跨模块依赖</b>：通过 {@link MuscleGroupRepository}（来自 fitcoach-exercise）校验 muscleGroup
 * 软外键有效；这样新增肌群只需在 muscle_group 表加一条，AI 动作和训练动作都能即刻使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingExerciseService {

    private final TrainingExerciseRepository trainingExerciseRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final TrainingExerciseIconStorageService iconStorageService;

    /** 当前允许的器械类型集合（与 {@link ResultCode#TRAINING_EXERCISE_EQUIPMENT_INVALID} 提示文案一致） */
    public static final Set<String> ALLOWED_EQUIPMENTS = Set.of(
            "BARBELL", "DUMBBELL", "MACHINE", "BODYWEIGHT", "CABLE", "CARDIO"
    );

    // ====== 查询 ======

    /**
     * 用户端列表：内置 + 当前用户自定义，仅启用，已按 sortOrder 升序排好。
     *
     * @param userId 当前登录用户 id（必传，用于过滤自定义动作的归属）
     */
    public List<TrainingExercise> listVisibleForUser(Long userId) {
        return trainingExerciseRepository.findVisibleEnabledForUser(userId);
    }

    /** Admin 列表：全部内置动作（含禁用） */
    public List<TrainingExercise> listAllBuiltin() {
        return trainingExerciseRepository.findAllBuiltinOrderBySortOrder();
    }

    /** 按内部主键查（admin 详情/编辑用），找不到抛 TRAINING_EXERCISE_NOT_FOUND */
    public TrainingExercise findById(Long id) {
        return trainingExerciseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.TRAINING_EXERCISE_NOT_FOUND));
    }

    /**
     * 按 key 查内置动作（Seeder / 训练记录引用校验用）。
     * <p>找不到抛 TRAINING_EXERCISE_NOT_FOUND；启用状态不在此处校验，由调用方判断。
     */
    public TrainingExercise findBuiltinByKey(String exerciseKey) {
        return trainingExerciseRepository.findByExerciseKeyAndUserIdIsNull(exerciseKey)
                .orElseThrow(() -> new BusinessException(ResultCode.TRAINING_EXERCISE_NOT_FOUND));
    }

    // ====== Admin 写操作（内置动作） ======

    /**
     * 创建内置动作（admin 端用）。强制 userId = null / isCustom = false。
     * <p>校验：exerciseKey 在内置范围唯一、muscleGroup 引用有效、equipment 合法。
     */
    public TrainingExercise createBuiltin(TrainingExercise toCreate) {
        // 1. exerciseKey 唯一（仅在内置范围）
        trainingExerciseRepository.findByExerciseKeyAndUserIdIsNull(toCreate.getExerciseKey())
                .ifPresent(t -> {
                    throw new BusinessException(ResultCode.TRAINING_EXERCISE_KEY_DUPLICATE);
                });
        // 2. muscleGroup 必须存在
        if (toCreate.getMuscleGroup() == null
                || !muscleGroupRepository.existsByGroupKey(toCreate.getMuscleGroup())) {
            throw new BusinessException(ResultCode.MUSCLE_GROUP_NOT_FOUND);
        }
        // 3. equipment 必须在白名单
        if (toCreate.getEquipment() == null || !ALLOWED_EQUIPMENTS.contains(toCreate.getEquipment())) {
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_EQUIPMENT_INVALID);
        }
        // 4. 强制走 admin 内置创建语义
        toCreate.setId(null);
        toCreate.setUserId(null);
        toCreate.setIsCustom(false);
        if (toCreate.getEnabled() == null) toCreate.setEnabled(true);
        if (toCreate.getSortOrder() == null) toCreate.setSortOrder(0);

        TrainingExercise saved = trainingExerciseRepository.save(toCreate);
        log.info("[training-exercise] 创建内置动作 id={} key={} muscleGroup={} equipment={}",
                saved.getId(), saved.getExerciseKey(), saved.getMuscleGroup(), saved.getEquipment());
        return saved;
    }

    /**
     * 更新动作（PATCH 语义：null = 不动）。
     * <p>不允许修改 exerciseKey / userId / isCustom（这三个改了会破坏唯一约束 + 引用关系）。
     */
    public TrainingExercise update(Long id, TrainingExercise patch) {
        TrainingExercise existing = findById(id);

        if (patch.getDisplayName() != null) existing.setDisplayName(patch.getDisplayName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getEmoji() != null) existing.setEmoji(patch.getEmoji());
        if (patch.getMuscleGroup() != null) {
            if (!muscleGroupRepository.existsByGroupKey(patch.getMuscleGroup())) {
                throw new BusinessException(ResultCode.MUSCLE_GROUP_NOT_FOUND);
            }
            existing.setMuscleGroup(patch.getMuscleGroup());
        }
        if (patch.getEquipment() != null) {
            if (!ALLOWED_EQUIPMENTS.contains(patch.getEquipment())) {
                throw new BusinessException(ResultCode.TRAINING_EXERCISE_EQUIPMENT_INVALID);
            }
            existing.setEquipment(patch.getEquipment());
        }
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());

        TrainingExercise saved = trainingExerciseRepository.save(existing);
        log.info("[training-exercise] 更新动作 id={} key={} enabled={}",
                saved.getId(), saved.getExerciseKey(), saved.getEnabled());
        return saved;
    }

    /**
     * 硬删除动作。
     *
     * <p>历史训练记录中引用本动作的 TrainingRecordExercise 不会受影响（它存的是 key + name 快照），
     * 因此可以安全删除；运营更常用的做法是 {@code enabled = false} 软下架。
     *
     * <p>会顺便删除该动作关联的自定义图标文件（如果有），避免遗留垃圾文件。
     */
    public void delete(Long id) {
        TrainingExercise existing = findById(id);
        // 顺手把图标文件清掉（DB 行马上就要删，磁盘文件留着也没用）
        if (existing.getIconUrl() != null) {
            iconStorageService.deleteIconByUrl(existing.getIconUrl());
        }
        trainingExerciseRepository.delete(existing);
        log.info("[training-exercise] 删除动作 id={} key={}", id, existing.getExerciseKey());
    }

    // ====== Admin 写操作（图标专用接口） ======

    /**
     * 上传 / 替换训练动作的自定义图标。
     *
     * <p>覆盖语义：若该动作已有图标文件，先删除旧文件再保存新文件，并更新 DB 中的 iconUrl。
     *
     * @param id   动作 ID
     * @param file 上传图标（JPEG/PNG/WebP，≤ 512KB）
     * @return 更新后的动作实体（iconUrl 已回填新地址）
     */
    public TrainingExercise attachIconFile(Long id, org.springframework.web.multipart.MultipartFile file) {
        TrainingExercise existing = findById(id);
        // 先删旧图（不影响 DB，文件不存在也不报错）
        if (existing.getIconUrl() != null) {
            iconStorageService.deleteIconByUrl(existing.getIconUrl());
        }
        TrainingExerciseIconStorageService.IconStoreResult result =
                iconStorageService.saveIcon(existing.getExerciseKey(), file);
        existing.setIconUrl(result.url());
        TrainingExercise saved = trainingExerciseRepository.save(existing);
        log.info("[training-exercise] 图标已上传 id={} key={} url={} size={}B",
                saved.getId(), saved.getExerciseKey(), saved.getIconUrl(), result.size());
        return saved;
    }

    /**
     * 删除训练动作的自定义图标（仅删文件 + 清 iconUrl 字段，不删动作记录）。
     * <p>删除后客户端会回落到 emoji 渲染。
     *
     * @param id 动作 ID
     * @return 更新后的动作实体（iconUrl 已清空）
     */
    public TrainingExercise removeIconFile(Long id) {
        TrainingExercise existing = findById(id);
        if (existing.getIconUrl() != null) {
            iconStorageService.deleteIconByUrl(existing.getIconUrl());
            existing.setIconUrl(null);
            existing = trainingExerciseRepository.save(existing);
            log.info("[training-exercise] 图标已删除 id={} key={}", existing.getId(), existing.getExerciseKey());
        }
        return existing;
    }
}
