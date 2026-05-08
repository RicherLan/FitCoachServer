package com.lanprojects.fitcoach.exercise.dto;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.entity.MuscleGroup;
import lombok.Builder;
import lombok.Data;

/**
 * 客户端动作列表项 DTO。
 *
 * <p>**与 Entity 的差异**：
 * <ul>
 *   <li>不暴露内部主键 id（客户端用 exerciseKey 唯一标识动作）；</li>
 *   <li>cameraSetupJson 透传给客户端（RN 端 CameraSetup 类型），server 不解析；</li>
 *   <li>不暴露 sortOrder / enabled / 时间戳等运营字段（服务端已经按 sortOrder 排好序，
 *       客户端按返回顺序展示即可）。</li>
 * </ul>
 *
 * <p>注意 muscleGroup 用 enum.name() 字符串传给客户端，避免序列化数字让客户端硬编码。
 */
@Data
@Builder
public class ExerciseDTO {

    /** 业务 key，例：SQUAT */
    private String exerciseKey;

    private String displayName;

    private String description;

    /** 涉及肌群描述（人类可读） */
    private String muscles;

    /** 表情符号 */
    private String emoji;

    /** 肌群分类，例："CHEST" / "LEGS"，与 RN 端 MuscleGroup 枚举对齐 */
    private String muscleGroup;

    /** 客户端 CameraSetup JSON（透传，server 不解析） */
    private String cameraSetupJson;

    /** 是否免费（客户端用于在卡片打"免费体验"标签） */
    private Boolean isFree;

    public static ExerciseDTO from(Exercise e) {
        MuscleGroup g = e.getMuscleGroup();
        return ExerciseDTO.builder()
                .exerciseKey(e.getExerciseKey())
                .displayName(e.getDisplayName())
                .description(e.getDescription())
                .muscles(e.getMuscles())
                .emoji(e.getEmoji())
                .muscleGroup(g != null ? g.name() : null)
                .cameraSetupJson(e.getCameraSetupJson())
                .isFree(Boolean.TRUE.equals(e.getIsFree()))
                .build();
    }
}
