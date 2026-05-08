package com.lanprojects.fitcoach.admin.dto.exercise;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.entity.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Admin 端动作创建/更新入参。
 *
 * <p>语义约定（与其他 admin 模块对齐）：
 * <ul>
 *   <li>创建时必须传：exerciseKey / displayName / muscleGroup（其余字段可空）；</li>
 *   <li>更新时（PATCH）所有字段可空，{@code null} 表示不动；</li>
 *   <li>{@code exerciseKey} 一旦发布禁止改名（{@link com.lanprojects.fitcoach.exercise.service.ExerciseService#update}
 *       不会读取 patch 的 exerciseKey 字段，这里只在创建时用）。</li>
 * </ul>
 */
@Data
public class AdminExerciseRequest {

    /** 全大写下划线，例：SQUAT。一旦发布禁止改名 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$",
            message = "exerciseKey 必须是大写字母开头、含大写字母/数字/下划线、长度 2-64",
            groups = {OnCreate.class, OnUpdate.class})
    private String exerciseKey;

    @NotBlank(groups = OnCreate.class)
    private String displayName;

    private String description;

    private String muscles;

    private String emoji;

    @NotNull(groups = OnCreate.class)
    private MuscleGroup muscleGroup;

    /** RN 端 CameraSetup 的 JSON 字符串（透传，server 不解析） */
    private String cameraSetupJson;

    /** 默认 false（付费） */
    private Boolean isFree;

    private Integer sortOrder;

    /** 默认 true */
    private Boolean enabled;

    /**
     * 转换为新建用的 Exercise 实体（仅创建时使用）。
     */
    public Exercise toCreateEntity() {
        Exercise e = new Exercise();
        e.setExerciseKey(exerciseKey);
        e.setDisplayName(displayName);
        e.setDescription(description);
        e.setMuscles(muscles);
        e.setEmoji(emoji);
        e.setMuscleGroup(muscleGroup);
        e.setCameraSetupJson(cameraSetupJson);
        e.setIsFree(isFree != null ? isFree : Boolean.FALSE);
        e.setSortOrder(sortOrder != null ? sortOrder : 0);
        e.setEnabled(enabled != null ? enabled : Boolean.TRUE);
        return e;
    }

    /**
     * 把 PATCH 请求里非 null 的字段叠加到现有实体上（实际更新由
     * {@link com.lanprojects.fitcoach.exercise.service.ExerciseService#update} 完成；
     * 此方法只是 controller 把请求转为 patch 容器供 service 使用）。
     */
    public Exercise toPatchEntity() {
        Exercise patch = new Exercise();
        // exerciseKey 不允许更新，故不设置
        patch.setDisplayName(displayName);
        patch.setDescription(description);
        patch.setMuscles(muscles);
        patch.setEmoji(emoji);
        patch.setMuscleGroup(muscleGroup);
        patch.setCameraSetupJson(cameraSetupJson);
        patch.setIsFree(isFree);
        patch.setSortOrder(sortOrder);
        patch.setEnabled(enabled);
        return patch;
    }

    /** 仅创建时校验 */
    public interface OnCreate {}

    /** 仅更新时校验 */
    public interface OnUpdate {}
}
