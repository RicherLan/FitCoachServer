package com.lanprojects.fitcoach.trainingrecord.dto;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import lombok.Builder;
import lombok.Data;

/**
 * 训练动作 - 客户端 DTO。
 *
 * <p>用户端列表项，从 {@link TrainingExercise} 转换。
 * <ul>
 *   <li>不暴露内部主键 id（客户端用 exerciseKey 唯一标识）；</li>
 *   <li>不暴露 enabled / sortOrder（已按服务端排序好，禁用项不返回）；</li>
 *   <li>暴露 isCustom 让客户端在 UI 上区分内置 vs 自定义（自定义可显示删除按钮）。</li>
 * </ul>
 */
@Data
@Builder
public class TrainingExerciseDTO {

    /** 业务 key，例："BARBELL_BENCH_PRESS" */
    private String exerciseKey;

    /** 显示名称 */
    private String displayName;

    /** 描述 / 训练要点 */
    private String description;

    /** 表情符号（iconUrl 加载失败 / 离线时的二级兜底） */
    private String emoji;

    /**
     * 自定义图标 URL（相对路径，例：{@code /static/trainingexercise-icon/BARBELL_BENCH_PRESS/xxx.png}）。
     * <p>客户端渲染优先级：{@code iconUrl > emoji > 本地内置兜底 emoji}。
     * <p>客户端 axios 实例已配置 baseURL，渲染时直接 {@code <Image source={{uri: baseURL + iconUrl}} />}。
     */
    private String iconUrl;

    /** 肌群 groupKey，例："CHEST" / "BICEPS" */
    private String muscleGroup;

    /** 器械类型：BARBELL / DUMBBELL / MACHINE / BODYWEIGHT / CABLE / CARDIO */
    private String equipment;

    /** 是否用户自定义动作（false=内置，true=自己创建的） */
    private Boolean isCustom;

    public static TrainingExerciseDTO from(TrainingExercise t) {
        return TrainingExerciseDTO.builder()
                .exerciseKey(t.getExerciseKey())
                .displayName(t.getDisplayName())
                .description(t.getDescription())
                .emoji(t.getEmoji())
                .iconUrl(t.getIconUrl())
                .muscleGroup(t.getMuscleGroup())
                .equipment(t.getEquipment())
                .isCustom(Boolean.TRUE.equals(t.getIsCustom()))
                .build();
    }
}
