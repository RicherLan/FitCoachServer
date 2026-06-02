package com.lanprojects.fitcoach.trainingrecord.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 训练记录中的"某一组"层 — 最底层叶子节点。
 *
 * <p>一个动作（{@link TrainingRecordExercise}）通常有 3-5 组，每组记录"用多少重量做了多少次"。
 *
 * <p><b>重量永远存 kg</b>（{@link #weightKg}），数据库不存 lbs；客户端按用户偏好显示时做单位换算。
 * 这是 PRD §2 的核心决策——避免历史数据因用户切换偏好出现数值漂移。
 *
 * <p><b>自重动作</b>（如俯卧撑 / 引体）写 {@code weightKg = 0.0}（不是 null），
 * 容量计算 reps × 0 = 0，但仍计入 totalSets 维度。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_record_set", indexes = {
        @Index(name = "idx_trs_exercise_id", columnList = "exercise_id")
})
public class TrainingRecordSet extends BaseEntity {

    /** 所属动作条目（FK） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private TrainingRecordExercise exercise;

    /**
     * 组序号（1 起始，符合健身房口语习惯："第 1 组"）。
     * <p>客户端编辑时可任意调序；"删后插"语义下由 Service 在保存时重排。
     */
    @Column(name = "set_index", nullable = false)
    private Integer setIndex = 1;

    /**
     * 本组重量（kg，永远存 kg；自重 0.0；不允许 null）。
     * <p>步进默认 0.5；后端不限制精度，保留 1 位小数显示由客户端处理。
     */
    @Column(name = "weight_kg", nullable = false)
    private Double weightKg = 0.0;

    /**
     * 本组次数（≥ 1）。
     * <p>有氧动作此处填"换算成 reps 的数值"或留 0（PRD 未来再细化有氧的"距离/时长"独立字段）。
     */
    @Column(name = "reps", nullable = false)
    private Integer reps = 0;

    /**
     * 是否热身组（true = 不计入主训容量统计；false = 正式组）。
     * <p>MVP 默认全是 false（未开放热身组 UI），字段保留以备 P1。
     */
    @Column(name = "is_warmup", nullable = false)
    private Boolean isWarmup = false;
}
