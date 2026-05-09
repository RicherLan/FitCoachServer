package com.lanprojects.fitcoach.exercise.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 肌群分类（运营可在 admin 后台增删改、控制是否对客户端展示）。
 *
 * <p><b>命名说明</b>：本类故意取名 {@code MuscleGroupEntity}，与同包下的旧枚举
 * {@link MuscleGroup} 区分。两者的关系正在迁移：
 * <ul>
 *   <li>历史方案：{@link Exercise#getMuscleGroup()} 用 {@link MuscleGroup} 枚举，分类硬编码在客户端 + 服务端枚举里；</li>
 *   <li>新方案（当前）：肌群元数据下沉到本表，{@link Exercise#getMuscleGroup()} 后续将改为 String 软外键引用本表的
 *       {@link #groupKey}。详见 S3 步骤。</li>
 * </ul>
 *
 * <p><b>数据迁移</b>：现有 7 个枚举值（CHEST/BACK/LEGS/SHOULDERS/ARMS/CORE/FULL_BODY）由
 * MuscleGroupSeeder 在启动时预置到本表，确保不丢数据。
 *
 * <p><b>客户端使用</b>：客户端通过 {@code GET /api/muscle-group/list} 拉取启用的肌群（按 sortOrder 升序），
 * 用于首页类目展示。client 不需要硬编码肌群清单，新增/调整肌群只改 server，不发版。
 *
 * <p><b>"每个肌群至少 1 个免费动作"规则</b>：仍由 {@link Exercise} 维度强制
 * （见 ExerciseService.update 的保护逻辑），本表不参与该规则。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "muscle_group", indexes = {
        @Index(name = "uk_muscle_group_key", columnList = "group_key", unique = true),
        @Index(name = "idx_muscle_group_enabled", columnList = "enabled")
})
public class MuscleGroupEntity extends BaseEntity {

    /**
     * 肌群业务 key（与 {@link Exercise#getMuscleGroup()} 字段值对齐），全大写下划线。
     * <p>例：CHEST / BACK / LEGS / FULL_BODY。一旦发布禁止改名（会破坏已存在的 Exercise.muscleGroup 引用）。
     */
    @Column(name = "group_key", nullable = false, length = 32)
    private String groupKey;

    /** 显示名称（中文/可国际化），客户端首页类目卡片标题用。例："胸" "腿" */
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** 表情符号（首页类目卡片装饰用）。例："💪" "🦿" */
    @Column(name = "emoji", length = 8)
    private String emoji;

    /** 描述（admin 后台编辑提示用，客户端可选展示） */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 颜色（hex，如 "#FF5722"）。客户端首页类目卡片的背景/标签色，
     * 让不同肌群在视觉上有区分；为空时客户端用默认色。
     */
    @Column(name = "color", length = 16)
    private String color;

    /** 排序权重（小 → 前），客户端首页类目按此顺序展示 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 是否启用（admin 下架某个肌群设 false，客户端列表不再返回；
     * 该肌群下的所有动作在客户端首页也会被隐藏 —— 由客户端"空类目自动隐藏"逻辑兜底）。
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
