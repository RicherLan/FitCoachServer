package com.lanprojects.fitcoach.trainingrecord.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练记录中的"做过哪个动作"层。
 *
 * <p>从 {@link TrainingRecord} 级联保存 / 删除，是一次训练中的一个动作条目。
 *
 * <p><b>快照字段</b>（{@link #exerciseName} / {@link #muscleGroup} / {@link #equipment} / {@link #emoji}）：
 * 写入时从 {@code training_exercise} 表拷贝，之后即使原动作被改名 / 改肌群 / 下架，
 * 历史训练记录显示的依然是当时的快照值。这样用户的历史数据语义稳定。
 *
 * <p><b>软引用</b>：通过 {@link #exerciseKey} 软引用 {@code training_exercise.exercise_key}，
 * 不建外键。原动作被删时本表不报错，仅 key 失效（客户端看到 key 但无法点详情）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_record_exercise", indexes = {
        @Index(name = "idx_tre_record_id", columnList = "record_id"),
        @Index(name = "idx_tre_exercise_key", columnList = "exercise_key")
})
public class TrainingRecordExercise extends BaseEntity {

    /** 所属训练记录（FK） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private TrainingRecord record;

    /**
     * 在 TrainingRecord.exercises 数组中的位置（0 起始），用于保留客户端编排顺序。
     * <p>"删后插"语义下由 Service 重新赋值。
     */
    @Column(name = "position", nullable = false)
    private Integer position = 0;

    /**
     * 动作业务 key（软引用 {@code training_exercise.exercise_key}）。
     * <p>客户端按 key 查询动作详情；服务端不强制校验有效性（允许引用已删动作的历史记录），
     * 仅在创建时由 Service 校验一次"当时存在且启用"。
     */
    @Column(name = "exercise_key", nullable = false, length = 64)
    private String exerciseKey;

    /** 动作名称快照（写入时从 training_exercise.display_name 拷贝） */
    @Column(name = "exercise_name", nullable = false, length = 128)
    private String exerciseName;

    /** 肌群快照（如 "CHEST"），用于 TrainingRecord.muscleGroupsCsv 的聚合计算 */
    @Column(name = "muscle_group", nullable = false, length = 32)
    private String muscleGroup;

    /** 器械类型快照（如 "BARBELL"），客户端展示用 */
    @Column(name = "equipment", length = 32)
    private String equipment;

    /** emoji 快照 */
    @Column(name = "emoji", length = 8)
    private String emoji;

    /**
     * 自定义图标 URL 快照（写入时从 {@code training_exercise.icon_url} 拷贝）。
     * <p>历史训练记录展示时**优先**渲染该 URL；为空回落到 {@link #emoji}。
     * 即便 admin 后续修改 / 删除原动作的图标，历史记录依旧显示当时上传的图标。
     */
    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    /**
     * 各组数据。级联保存 + 孤儿删除。
     * <p>插入顺序由 Service 在写入时按 setIndex 升序，查询用 @OrderBy 保证顺序稳定。
     */
    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("setIndex ASC")
    private List<TrainingRecordSet> sets = new ArrayList<>();

    public void addSet(TrainingRecordSet set) {
        set.setExercise(this);
        this.sets.add(set);
    }
}
