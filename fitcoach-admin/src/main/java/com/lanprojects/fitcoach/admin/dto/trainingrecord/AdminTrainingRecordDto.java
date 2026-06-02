package com.lanprojects.fitcoach.admin.dto.trainingrecord;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Admin 端「用户训练记录」列表项概要 DTO。
 *
 * <p>只含顶层字段 + 用户冗余 + 动作条目数（exerciseCount）；不展开三层 exercises/sets。
 * 详情请用 {@link AdminTrainingRecordDetailDto}。
 *
 * <p><b>userUid / userNickname</b> 不从 entity 派生，由 Controller 单独 join user 表后 set 进来，
 * 与 {@code AdminPaymentOrderDto} 同样的 enrichWithUser 套路。
 */
@Data
@Builder
public class AdminTrainingRecordDto {

    /** 服务端内部 id（详情接口要用） */
    private Long id;

    /** 客户端幂等标识（便于排查重试问题） */
    private String clientId;

    /** 所属用户 id */
    private Long userId;

    /** 用户业务 uid（controller join 出来填上） */
    private String userUid;

    /** 用户昵称（controller join 出来填上） */
    private String userNickname;

    /** 训练日期 */
    private LocalDate date;

    /** 训练开始时刻 */
    private LocalDateTime startedAt;

    /** 训练结束时刻 */
    private LocalDateTime endedAt;

    /** 训练时长（分钟） */
    private Integer durationMin;

    /** 用户备注（preview，列表页可能截断） */
    private String note;

    /** 涉及肌群（按字典序，例 ["BACK","BICEPS"]） */
    private List<String> muscleGroups;

    /** 总训练容量（kg） */
    private Double totalVolumeKg;

    /** 总组数 */
    private Integer totalSets;

    /** 动作条目数（exercises.size，不展开内容） */
    private Integer exerciseCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从 entity 构造概要 DTO。userUid / userNickname 留空，由 controller 后续 enrich。
     *
     * <p><b>性能注意</b>：访问 {@code r.getExercises().size()} 在 LAZY 模式下会触发一次 SQL 拉子表，
     * 列表场景大批量调用时建议用 EntityGraph 一次 fetch；当前 admin 列表分页规模可控（≤200/页），
     * 一次 N+1 暂时可接受。如后续观察到性能瓶颈再优化。
     */
    public static AdminTrainingRecordDto from(TrainingRecord r) {
        return AdminTrainingRecordDto.builder()
                .id(r.getId())
                .clientId(r.getClientId())
                .userId(r.getUserId())
                .date(r.getDate())
                .startedAt(r.getStartedAt())
                .endedAt(r.getEndedAt())
                .durationMin(r.getDurationMin())
                .note(r.getNote())
                .muscleGroups(splitCsv(r.getMuscleGroupsCsv()))
                .totalVolumeKg(r.getTotalVolumeKg())
                .totalSets(r.getTotalSets())
                .exerciseCount(r.getExercises() == null ? 0 : r.getExercises().size())
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
}
