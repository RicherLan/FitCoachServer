package com.lanprojects.fitcoach.admin.dto.trainingexercise;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端训练动作 DTO（包含 isCustom / userId / enabled / 时间戳等运营字段）。
 *
 * <p>与客户端 {@code TrainingExerciseDTO}（用户端列表用）相比，admin 视图多出：
 * <ul>
 *   <li>{@link #enabled} —— admin 后台需要看到禁用项以便恢复上架；</li>
 *   <li>{@link #isCustom}/{@link #userId} —— 运营查看用户自定义动作的归属（MVP 不开放，预留）；</li>
 *   <li>{@link #createdAt}/{@link #updatedAt} —— 审计与排查问题用。</li>
 * </ul>
 */
@Data
@Builder
public class AdminTrainingExerciseDto {

    private Long id;
    private String exerciseKey;
    private String displayName;
    private String description;
    private String emoji;
    /**
     * 自定义图标 URL（admin 上传后回填，相对路径如 {@code /static/trainingexercise-icon/...}）。
     * <p>前端列表优先用本字段渲染图标，为空时回落到 emoji。
     */
    private String iconUrl;
    private String muscleGroup;
    private String equipment;
    private Boolean isCustom;
    private Long userId;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminTrainingExerciseDto from(TrainingExercise e) {
        return AdminTrainingExerciseDto.builder()
                .id(e.getId())
                .exerciseKey(e.getExerciseKey())
                .displayName(e.getDisplayName())
                .description(e.getDescription())
                .emoji(e.getEmoji())
                .iconUrl(e.getIconUrl())
                .muscleGroup(e.getMuscleGroup())
                .equipment(e.getEquipment())
                .isCustom(Boolean.TRUE.equals(e.getIsCustom()))
                .userId(e.getUserId())
                .sortOrder(e.getSortOrder())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
