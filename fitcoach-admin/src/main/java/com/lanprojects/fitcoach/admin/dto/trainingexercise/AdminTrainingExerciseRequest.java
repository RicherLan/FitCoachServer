package com.lanprojects.fitcoach.admin.dto.trainingexercise;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Admin 端训练动作创建/更新入参（与 {@code AdminExerciseRequest} 对齐的语义约定）。
 *
 * <ul>
 *   <li>创建（OnCreate）必传：exerciseKey / displayName / muscleGroup / equipment；</li>
 *   <li>更新（OnUpdate）所有字段可空，null = 不动；exerciseKey 一旦发布禁止改名（Service 内会忽略 patch 中的 key）；</li>
 *   <li>器械白名单由 {@code TrainingExerciseService.ALLOWED_EQUIPMENTS} 维护；</li>
 *   <li>{@code muscleGroup} 必须存在于 {@code muscle_group} 字典（{@code /api/admin/muscle-groups}）。</li>
 * </ul>
 */
@Data
public class AdminTrainingExerciseRequest {

    /** 全大写下划线，例：BARBELL_BENCH_PRESS。一旦发布禁止改名 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$",
            message = "exerciseKey 必须是大写字母开头、含大写字母/数字/下划线、长度 2-64",
            groups = {OnCreate.class, OnUpdate.class})
    private String exerciseKey;

    @NotBlank(groups = OnCreate.class)
    private String displayName;

    private String description;

    private String emoji;

    /** 肌群 groupKey（如 "CHEST"）。需先在肌群字典存在，否则 7601 MUSCLE_GROUP_NOT_FOUND */
    @NotBlank(groups = OnCreate.class)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$",
            message = "muscleGroup 必须是大写字母开头、含大写字母/数字/下划线、长度 2-32",
            groups = {OnCreate.class})
    private String muscleGroup;

    /** 器械类型：BARBELL/DUMBBELL/MACHINE/BODYWEIGHT/CABLE/CARDIO */
    @NotBlank(groups = OnCreate.class)
    private String equipment;

    private Integer sortOrder;

    /** 默认 true */
    private Boolean enabled;

    public TrainingExercise toCreateEntity() {
        TrainingExercise e = new TrainingExercise();
        e.setExerciseKey(exerciseKey);
        e.setDisplayName(displayName);
        e.setDescription(description);
        e.setEmoji(emoji);
        e.setMuscleGroup(muscleGroup);
        e.setEquipment(equipment);
        e.setSortOrder(sortOrder != null ? sortOrder : 0);
        e.setEnabled(enabled != null ? enabled : Boolean.TRUE);
        // userId / isCustom 由 Service.createBuiltin() 强制覆盖为 null/false
        return e;
    }

    public TrainingExercise toPatchEntity() {
        TrainingExercise patch = new TrainingExercise();
        // exerciseKey 不允许更新，故不设置
        patch.setDisplayName(displayName);
        patch.setDescription(description);
        patch.setEmoji(emoji);
        patch.setMuscleGroup(muscleGroup);
        patch.setEquipment(equipment);
        patch.setSortOrder(sortOrder);
        patch.setEnabled(enabled);
        return patch;
    }

    public interface OnCreate {}
    public interface OnUpdate {}
}
