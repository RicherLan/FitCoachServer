package com.lanprojects.fitcoach.trainingrecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 / 更新训练记录的请求体（客户端 → server）。
 *
 * <p><b>幂等约定</b>：客户端必须传 {@link #clientId}（UUID 字符串），
 * 同 (userId, clientId) 重复提交会走"找到则更新"路径（详见 TrainingRecordService.createOrReplace）。
 *
 * <p><b>校验</b>：
 * <ul>
 *   <li>{@link #date} 必填，允许未来日期（PRD §2 决策）；</li>
 *   <li>{@link #exercises} 至少 1 个动作（空提交无意义）；</li>
 *   <li>每个动作至少 1 组（{@link ExerciseItem#sets}）；</li>
 *   <li>每组 weightKg ≥ 0、reps ≥ 1 —— 由嵌套校验保证。</li>
 * </ul>
 */
@Data
public class TrainingRecordRequest {

    /**
     * 客户端幂等标识。重试 / 离线队列重发时保持一致；同 userId 重复提交会更新原记录。
     */
    @NotBlank(message = "缺少幂等标识 clientId")
    @Size(max = 64)
    private String clientId;

    /** 训练日期（必填，允许未来日期） */
    @NotNull(message = "训练日期必填")
    private LocalDate date;

    /** 训练开始时刻（可选） */
    private LocalDateTime startedAt;

    /** 训练结束时刻（可选） */
    private LocalDateTime endedAt;

    /**
     * 训练时长（分钟，可选）。
     * <p>缺失时 server 尝试用 endedAt - startedAt 自动计算；都缺则存 null。
     */
    @Min(value = 0, message = "训练时长不能为负")
    private Integer durationMin;

    /** 备注（500 字内） */
    @Size(max = 500, message = "备注最多 500 字")
    private String note;

    /** 训练动作列表（至少 1 个） */
    @NotEmpty(message = "至少添加一个训练动作")
    @Valid
    private List<ExerciseItem> exercises;

    /** 单个动作条目 */
    @Data
    public static class ExerciseItem {

        /**
         * 动作业务 key（必传，软引用 training_exercise.exercise_key）。
         * Server 会校验该 key 是否存在 + 启用；找不到时抛 TRAINING_RECORD_EXERCISE_NOT_FOUND。
         */
        @NotBlank(message = "动作 key 必填")
        @Size(max = 64)
        private String exerciseKey;

        /** 至少 1 组 */
        @NotEmpty(message = "每个动作至少需要一组")
        @Valid
        private List<SetItem> sets;
    }

    /** 单组数据 */
    @Data
    public static class SetItem {

        /**
         * 重量（kg）。
         * <p>自重动作传 0.0；不允许 null 也不允许负值。
         */
        @NotNull(message = "重量必填")
        @Min(value = 0, message = "重量必须 ≥ 0")
        private Double weightKg;

        /** 次数（≥ 1） */
        @NotNull(message = "次数必填")
        @Min(value = 1, message = "次数必须 ≥ 1")
        private Integer reps;

        /** 是否热身组（默认 false） */
        private Boolean isWarmup = false;
    }
}
