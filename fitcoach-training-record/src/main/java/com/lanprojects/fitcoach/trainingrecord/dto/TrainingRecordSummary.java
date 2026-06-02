package com.lanprojects.fitcoach.trainingrecord.dto;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecordExercise;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 训练记录 - 列表项轻量 DTO（不含 sets 详情）。
 *
 * <p>用户端 list 接口默认返回 Summary 数组以减少传输体积；
 * 用户点开详情时再用 {@code GET /api/training-record/{id}} 拉完整 {@link TrainingRecordDTO}。
 *
 * <p>列表项仍带 {@code exerciseNames}（前 3 个动作名拼接），方便客户端列表卡片直接展示。
 */
@Data
@Builder
public class TrainingRecordSummary {

    private Long id;
    private String clientId;
    private LocalDate date;
    private Integer durationMin;

    /** 涉及肌群（拆分 CSV 后的数组） */
    private List<String> muscleGroups;

    /** 总训练容量（kg） */
    private Double totalVolumeKg;

    /** 总组数 */
    private Integer totalSets;

    /** 动作数量（exercises.size） */
    private Integer exerciseCount;

    /**
     * 前 3 个动作名称（卡片预览用），例：["杠铃卧推", "上斜杠铃卧推", "哑铃飞鸟"]。
     * <p>超过 3 个时只取前 3，让客户端展示"+2 个"等省略号。
     */
    private List<String> previewExerciseNames;

    private LocalDateTime createdAt;

    public static TrainingRecordSummary from(TrainingRecord r) {
        List<String> preview = r.getExercises().stream()
                .limit(3)
                .map(TrainingRecordExercise::getExerciseName)
                .toList();
        return TrainingRecordSummary.builder()
                .id(r.getId())
                .clientId(r.getClientId())
                .date(r.getDate())
                .durationMin(r.getDurationMin())
                .muscleGroups(splitCsv(r.getMuscleGroupsCsv()))
                .totalVolumeKg(r.getTotalVolumeKg())
                .totalSets(r.getTotalSets())
                .exerciseCount(r.getExercises().size())
                .previewExerciseNames(preview)
                .createdAt(r.getCreatedAt())
                .build();
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
