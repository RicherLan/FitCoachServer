package com.lanprojects.fitcoach.admin.dto.musclegroup;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端肌群 DTO（含 sortOrder / enabled / 时间戳等运营字段，与客户端 MuscleGroupDTO 区分）。
 */
@Data
@Builder
public class AdminMuscleGroupDto {

    private Long id;
    private String groupKey;
    private String displayName;
    private String emoji;
    private String description;
    private String color;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminMuscleGroupDto from(MuscleGroupEntity g) {
        return AdminMuscleGroupDto.builder()
                .id(g.getId())
                .groupKey(g.getGroupKey())
                .displayName(g.getDisplayName())
                .emoji(g.getEmoji())
                .description(g.getDescription())
                .color(g.getColor())
                .sortOrder(g.getSortOrder())
                .enabled(Boolean.TRUE.equals(g.getEnabled()))
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
