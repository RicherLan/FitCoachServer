package com.lanprojects.fitcoach.exercise.dto;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import lombok.Builder;
import lombok.Data;

/**
 * 客户端肌群列表项 DTO。
 *
 * <p><b>与 Entity 的差异</b>：
 * <ul>
 *   <li>不暴露内部主键 id（客户端用 {@code groupKey} 唯一标识肌群，与 Exercise.muscleGroup 字段对齐）；</li>
 *   <li>不暴露 sortOrder / enabled / 时间戳（服务端已按 sortOrder 排好序，客户端按返回顺序展示即可）。</li>
 * </ul>
 *
 * <p>客户端首页类目卡片使用：{@link #emoji} 头像 + {@link #displayName} 标题 + {@link #color} 配色装饰。
 */
@Data
@Builder
public class MuscleGroupDTO {

    /** 肌群业务 key（客户端用于和 Exercise.muscleGroup 字段做映射），例："CHEST" */
    private String groupKey;

    /** 显示名称（中文/可国际化），例："胸" */
    private String displayName;

    /** 表情符号，例："💪" */
    private String emoji;

    /** 描述（客户端可选展示） */
    private String description;

    /** 配色（hex，客户端类目卡片背景/标签色），例："#FF5722"；为空时客户端用默认色 */
    private String color;

    public static MuscleGroupDTO from(MuscleGroupEntity g) {
        return MuscleGroupDTO.builder()
                .groupKey(g.getGroupKey())
                .displayName(g.getDisplayName())
                .emoji(g.getEmoji())
                .description(g.getDescription())
                .color(g.getColor())
                .build();
    }
}
