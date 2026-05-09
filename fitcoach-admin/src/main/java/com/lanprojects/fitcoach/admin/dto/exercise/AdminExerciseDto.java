package com.lanprojects.fitcoach.admin.dto.exercise;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端动作 DTO（含 sortOrder / enabled / 时间戳等运营字段，与客户端 ExerciseDTO 区分）。
 */
@Data
@Builder
public class AdminExerciseDto {

    private Long id;
    private String exerciseKey;
    private String displayName;
    private String description;
    private String muscles;
    private String emoji;
    /** 肌群 groupKey 字符串（软外键引用 muscle_group.group_key） */
    private String muscleGroup;
    /** RN 端 CameraSetup 的 JSON 透传字段；admin 编辑时可直接看 raw JSON */
    private String cameraSetupJson;
    private Boolean isFree;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminExerciseDto from(Exercise e) {
        return AdminExerciseDto.builder()
                .id(e.getId())
                .exerciseKey(e.getExerciseKey())
                .displayName(e.getDisplayName())
                .description(e.getDescription())
                .muscles(e.getMuscles())
                .emoji(e.getEmoji())
                .muscleGroup(e.getMuscleGroup())
                .cameraSetupJson(e.getCameraSetupJson())
                .isFree(Boolean.TRUE.equals(e.getIsFree()))
                .sortOrder(e.getSortOrder())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
