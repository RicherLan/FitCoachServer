package com.lanprojects.fitcoach.exercise.entity;

/**
 * 肌群分类 — 用于动作分类、列表分组、"每个大类至少保留一个免费动作"规则的判定依据。
 *
 * <p>枚举值故意保持稳定（写库用 EnumType.STRING），新增分类只追加不重排。
 */
public enum MuscleGroup {
    CHEST,      // 胸
    BACK,       // 背
    LEGS,       // 腿
    SHOULDERS,  // 肩
    ARMS,       // 臂
    CORE,       // 核心
    FULL_BODY   // 全身复合
}
