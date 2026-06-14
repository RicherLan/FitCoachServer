package com.lanprojects.fitcoach.trainingrecord.dto;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecordExercise;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecordSet;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 训练记录 - 完整结构 DTO（server → 客户端）。
 *
 * <p>响应详情 / 列表项都用本结构。三层嵌套与 Entity 一一对应。
 *
 * <p><b>列表场景的优化</b>：列表展示通常只需要顶层字段 + muscleGroups / totalVolumeKg / totalSets，
 * 客户端可只用 {@link TrainingRecordSummary} 这个轻量版（不含 exercises 数组）。
 */
@Data
@Builder
public class TrainingRecordDTO {

    /** 服务端内部 id（删除 / 更新接口要用） */
    private Long id;

    /** 客户端幂等标识，回显给客户端做对账 */
    private String clientId;

    private LocalDate date;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer durationMin;
    private String note;

    /** 涉及肌群（按字典序，例 ["BACK","BICEPS"]）— 由 muscleGroupsCsv 拆分得到 */
    private List<String> muscleGroups;

    /** 总训练容量（kg） */
    private Double totalVolumeKg;

    /** 总组数 */
    private Integer totalSets;

    /** 动作列表 */
    private List<ExerciseDTO> exercises;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TrainingRecordDTO from(TrainingRecord r) {
        return TrainingRecordDTO.builder()
                .id(r.getId())
                .clientId(r.getClientId())
                .date(r.getDate())
                .startedAt(r.getStartedAt())
                .endedAt(r.getEndedAt())
                .durationMin(r.getDurationMin())
                .note(r.getNote())
                .muscleGroups(splitCsv(r.getMuscleGroupsCsv()))
                .totalVolumeKg(r.getTotalVolumeKg())
                .totalSets(r.getTotalSets())
                .exercises(r.getExercises().stream().map(ExerciseDTO::from).toList())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 单条动作 */
    @Data
    @Builder
    public static class ExerciseDTO {
        private Long id;
        private Integer position;
        private String exerciseKey;
        private String exerciseName;
        private String muscleGroup;
        private String equipment;
        private String emoji;
        /** 自定义图标 URL 快照（相对路径，前端用 baseURL 拼成完整 URL）。优先级高于 emoji。 */
        private String iconUrl;
        private List<SetDTO> sets;

        public static ExerciseDTO from(TrainingRecordExercise e) {
            return ExerciseDTO.builder()
                    .id(e.getId())
                    .position(e.getPosition())
                    .exerciseKey(e.getExerciseKey())
                    .exerciseName(e.getExerciseName())
                    .muscleGroup(e.getMuscleGroup())
                    .equipment(e.getEquipment())
                    .emoji(e.getEmoji())
                    .iconUrl(e.getIconUrl())
                    .sets(e.getSets().stream().map(SetDTO::from).toList())
                    .build();
        }
    }

    /** 单组 */
    @Data
    @Builder
    public static class SetDTO {
        private Long id;
        private Integer setIndex;
        private Double weightKg;
        private Integer reps;
        private Boolean isWarmup;

        public static SetDTO from(TrainingRecordSet s) {
            return SetDTO.builder()
                    .id(s.getId())
                    .setIndex(s.getSetIndex())
                    .weightKg(s.getWeightKg())
                    .reps(s.getReps())
                    .isWarmup(Boolean.TRUE.equals(s.getIsWarmup()))
                    .build();
        }
    }
}
