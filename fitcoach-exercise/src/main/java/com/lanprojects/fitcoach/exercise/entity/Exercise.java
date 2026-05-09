package com.lanprojects.fitcoach.exercise.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 健身动作元数据。
 *
 * <p><b>从 RN 端硬编码搬迁过来</b>（原位置：FitCoachRN/src/training/constants/exercises.ts）。
 * 搬迁后客户端从 server 拉列表（带本地缓存）；新增动作只改 server，不发版。
 *
 * <p><b>免费/付费控制</b>：
 * <ul>
 *   <li>{@link #isFree} = true → 列表页打"免费体验"标签，所有用户可用；</li>
 *   <li>{@link #isFree} = false → 仅会员可用，调用动作能力时被 server 守卫拦截返回 8001 MEMBERSHIP_REQUIRED；</li>
 *   <li>由 admin 后台运营指定哪些动作免费，且每个肌群至少保留一个免费动作（业务规则保护，
 *       违反会返回 7504 EXERCISE_LAST_FREE_IN_GROUP）。</li>
 * </ul>
 *
 * <p><b>cameraSetupJson</b> 是 RN 端 {@code CameraSetup} 类型的 JSON 序列化结果（包含建议的相机方向、距离等）。
 * Server 不解析这个字段，只透传给客户端，便于客户端按动作给出拍摄引导。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exercise", indexes = {
        @Index(name = "uk_exercise_key", columnList = "exercise_key", unique = true),
        @Index(name = "idx_exercise_enabled", columnList = "enabled"),
        @Index(name = "idx_exercise_is_free", columnList = "is_free"),
        @Index(name = "idx_exercise_muscle_group", columnList = "muscle_group")
})
public class Exercise extends BaseEntity {

    /**
     * 动作业务 key（RN 客户端引用、analyzer 路由、统计上报都用这个），全大写下划线。
     * <p>例：SQUAT / BICEP_CURL / PUSH_UP。一旦发布禁止改名（会破坏客户端 analyzer 映射）。
     */
    @Column(name = "exercise_key", nullable = false, length = 64)
    private String exerciseKey;

    /**
     * 显示名称（中文/可国际化）
     */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 涉及肌群描述（人类可读，例："股四头肌 · 臀大肌 · 腘绳肌"）。
     * <p>结构化分组用 {@link #muscleGroup} 字段。
     */
    @Column(name = "muscles", length = 255)
    private String muscles;

    /**
     * 表情符号（列表页装饰用）
     */
    @Column(name = "emoji", length = 8)
    private String emoji;

    /**
     * 肌群分类 key（软外键引用 {@link MuscleGroupEntity#getGroupKey()}）。
     *
     * <p><b>历史</b>：原来是 {@link MuscleGroup} 枚举字段（{@code @Enumerated(EnumType.STRING)}）。
     * 现在肌群已下沉为运营可维护的实体（见 {@link MuscleGroupEntity}），
     * 本字段改为 String 软引用——admin 创建动作时会校验这个 key 存在于 muscle_group 表中。
     *
     * <p>底层数据库列类型 {@code VARCHAR(32)} 不变，原有 enum 写入的字符串值（CHEST/BACK/...）天然兼容，
     * 不需要数据迁移。
     */
    @Column(name = "muscle_group", nullable = false, length = 32)
    private String muscleGroup;

    /**
     * 客户端 CameraSetup 配置的 JSON 序列化（含建议的相机方向、距离等）。
     * <p>server 不解析，透传给客户端使用。
     */
    @Lob
    @Column(name = "camera_setup_json", columnDefinition = "LONGTEXT")
    private String cameraSetupJson;

    /**
     * 是否免费（列表页打"免费体验"标签 + 调用能力时不需会员）
     */
    @Column(name = "is_free", nullable = false)
    private Boolean isFree = false;

    /**
     * 排序权重（小 → 前）
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 是否启用（admin 下架某个动作设 false，列表不再返回）
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
