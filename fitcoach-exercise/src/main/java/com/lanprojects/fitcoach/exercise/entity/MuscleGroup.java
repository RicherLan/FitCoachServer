package com.lanprojects.fitcoach.exercise.entity;

/**
 * 肌群分类（历史枚举，保留作为协议字典参考）。
 *
 * <p><b>已 deprecated</b>：肌群元数据已下沉到 {@link MuscleGroupEntity}，
 * 由 admin 后台运营维护。新业务代码请勿引用本枚举：
 * <ul>
 *   <li>{@code Exercise.muscleGroup} 已改为 String 软外键引用 muscle_group.group_key；</li>
 *   <li>客户端已通过 {@code GET /api/muscle-group/list} 拉取实时肌群列表。</li>
 * </ul>
 *
 * <p>本类暂时保留是为：
 * <ol>
 *   <li>记录历史上有过的 7 个枚举值（CHEST/BACK/LEGS/SHOULDERS/ARMS/CORE/FULL_BODY），
 *       作为 MuscleGroupSeeder 预置数据的"白名单"参考；</li>
 *   <li>避免删除导致 git history 上下文丢失。</li>
 * </ol>
 *
 * <p>未来某次大改造时可彻底删除。
 */
@Deprecated(forRemoval = true)
public enum MuscleGroup {
    CHEST,      // 胸
    BACK,       // 背
    LEGS,       // 腿
    SHOULDERS,  // 肩
    ARMS,       // 臂
    CORE,       // 核心
    FULL_BODY   // 全身复合
}
