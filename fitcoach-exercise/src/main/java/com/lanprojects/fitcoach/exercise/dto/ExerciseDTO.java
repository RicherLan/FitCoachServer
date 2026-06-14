package com.lanprojects.fitcoach.exercise.dto;

import com.lanprojects.fitcoach.common.i18n.I18nText;
import com.lanprojects.fitcoach.exercise.entity.Exercise;
import lombok.Builder;
import lombok.Data;

/**
 * 客户端动作列表项 DTO。
 *
 * <p>**与 Entity 的差异**：
 * <ul>
 *   <li>不暴露内部主键 id（客户端用 exerciseKey 唯一标识动作）；</li>
 *   <li>cameraSetupJson 透传给客户端（RN 端 CameraSetup 类型），server 不解析；</li>
 *   <li>不暴露 sortOrder / enabled / 时间戳等运营字段（服务端已经按 sortOrder 排好序，
 *       客户端按返回顺序展示即可）。</li>
 * </ul>
 *
 * <p>注意 muscleGroup 用 enum.name() 字符串传给客户端，避免序列化数字让客户端硬编码。
 */
@Data
@Builder
public class ExerciseDTO {

    /** 业务 key，例：SQUAT */
    private String exerciseKey;

    private String displayName;

    private String description;

    /** 涉及肌群描述（人类可读） */
    private String muscles;

    /** 表情符号 */
    private String emoji;

    /** 肌群 groupKey，例："CHEST" / "LEGS"。客户端用此值与 muscle_group 列表数据做映射展示 */
    private String muscleGroup;

    /** 客户端 CameraSetup JSON（透传，server 不解析） */
    private String cameraSetupJson;

    /** 是否免费（客户端用于在卡片打"免费体验"标签） */
    private Boolean isFree;

    /**
     * 转 DTO 时自动按当前请求语言（{@link com.lanprojects.fitcoach.common.client.ClientContext#locale()}）
     * 解析多语言字段；i18n 缺失 / 未命中目标语言时回落到旧单语言字段（永远非 null）。
     * <p>非 HTTP 上下文（如定时任务、内部调用）下 locale 兜底为 zh-CN，相当于行为不变。
     */
    public static ExerciseDTO from(Exercise e) {
        return ExerciseDTO.builder()
                .exerciseKey(e.getExerciseKey())
                .displayName(I18nText.pick(e.getDisplayNameI18n(), e.getDisplayName()))
                .description(I18nText.pick(e.getDescriptionI18n(), e.getDescription()))
                .muscles(I18nText.pick(e.getMusclesI18n(), e.getMuscles()))
                .emoji(e.getEmoji())
                .muscleGroup(e.getMuscleGroup())
                .cameraSetupJson(e.getCameraSetupJson())
                .isFree(Boolean.TRUE.equals(e.getIsFree()))
                .build();
    }
}
