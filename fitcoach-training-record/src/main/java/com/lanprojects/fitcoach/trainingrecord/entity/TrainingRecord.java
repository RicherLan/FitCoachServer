package com.lanprojects.fitcoach.trainingrecord.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 训练记录（顶层）— 用户某天完成的一次完整训练。
 *
 * <p><b>三层结构</b>：
 * <pre>
 *   TrainingRecord (顶层 · 一次训练)
 *     ├─ TrainingRecordExercise[] (做了哪几个动作)
 *     │     ├─ TrainingRecordSet[] (每个动作几组 · 重量 · 次数)
 * </pre>
 *
 * <p><b>幂等设计</b>：客户端必须在创建时传入 {@link #clientId}（UUID），
 * 同 (userId, clientId) 唯一索引保证：网络重试 / 离线队列重发都只会创建 1 条记录。
 * 已存在的 clientId 走 update 语义（详见 TrainingRecordService.createOrReplace）。
 *
 * <p><b>数据冗余字段</b>：{@link #muscleGroupsCsv} / {@link #totalVolumeKg} / {@link #totalSets}
 * 由 Service 在保存时根据子表数据自动计算 + 写入，列表页查询时无需 JOIN 直接展示，性能更好。
 * 修改记录时这三个字段会被重新计算。
 *
 * <p><b>软引用而非外键</b>：本表通过 userId（Long）软引用 user 表；
 * TrainingRecordExercise 通过 exerciseKey（String）软引用 training_exercise 表。
 * 这样动作下架 / 用户软删都不会破坏历史训练记录。
 *
 * <p><b>重量单位</b>：永远存 kg（{@link TrainingRecordSet#getWeightKg()}），
 * 客户端显示时根据用户偏好转换为 lbs（1 kg = 2.20462 lbs，保留 1 位小数）。
 * 后端不存储用户的单位偏好，单位偏好放在 user 资料表（{@code preferredWeightUnit}）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_record", indexes = {
        // 幂等核心索引：同一用户的同一 clientId 唯一
        @Index(name = "uk_training_record_user_client", columnList = "user_id, client_id", unique = true),
        @Index(name = "idx_training_record_user_date", columnList = "user_id, date"),
        @Index(name = "idx_training_record_user_created", columnList = "user_id, created_at")
})
public class TrainingRecord extends BaseEntity {

    /**
     * 客户端幂等标识（UUID 字符串）。
     * <p>由 RN 客户端在创建训练记录时生成，重试时保持一致。
     * 同 (userId, clientId) 唯一索引保证：网络重试 / 离线队列多次提交都只会落库 1 条。
     */
    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    /** 所属用户 id（软引用 user 表，不建立外键） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 训练日期（用户主观选择的"哪天的训练"，与 createdAt 解耦）。
     * <p>支持未来日期（用户可提前录入次日的计划训练）；时区按客户端本地。
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * 训练开始时刻（用户在"开始训练"时点击的时间戳），可选。
     * <p>客户端"自动计时"模式下必填；纯手动记录场景可不填。
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 训练结束时刻，可选。配合 startedAt 算 durationMin */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 训练时长（分钟）。
     * <p>客户端可手动填，也可由 (endedAt - startedAt) 自动算。Service 在保存时优先用客户端传值，
     * 缺失时尝试从 startedAt/endedAt 计算。
     */
    @Column(name = "duration_min")
    private Integer durationMin;

    /** 用户备注（500 字内）— 训练感受 / 注意事项 / 教练点评等 */
    @Column(name = "note", length = 500)
    private String note;

    /**
     * 涉及肌群 CSV（自动计算，去重 + 按字典序）。
     * <p>例："BACK,BICEPS,CORE"。列表页可直接渲染肌群 chip，不用 JOIN exercises 表。
     */
    @Column(name = "muscle_groups_csv", length = 255)
    private String muscleGroupsCsv;

    /**
     * 总训练容量（kg）= Σ (weightKg × reps)，自动计算。
     * <p>用于列表页直接显示"今天总容量 12,500 kg"。
     */
    @Column(name = "total_volume_kg", nullable = false)
    private Double totalVolumeKg = 0.0;

    /** 总组数（所有 exercises 的 sets 求和），自动计算 */
    @Column(name = "total_sets", nullable = false)
    private Integer totalSets = 0;

    /**
     * 子动作列表（级联保存 / 删除 / orphanRemoval）。
     * <p>update 时采用"删后插"语义：清空 exercises 后按客户端提交的顺序重新插入，
     * 保证 position 干净；id 会变化但客户端按 clientId 判定身份。
     */
    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<TrainingRecordExercise> exercises = new ArrayList<>();

    /** 维护双向关联的辅助方法 */
    public void addExercise(TrainingRecordExercise ex) {
        ex.setRecord(this);
        this.exercises.add(ex);
    }
}
