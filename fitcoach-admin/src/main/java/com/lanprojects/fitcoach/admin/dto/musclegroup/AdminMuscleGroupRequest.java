package com.lanprojects.fitcoach.admin.dto.musclegroup;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin 端肌群创建/更新入参。
 *
 * <p>语义约定：
 * <ul>
 *   <li>创建时必须传：groupKey / displayName（其余字段可空）；</li>
 *   <li>更新时（PATCH）所有字段可空，{@code null} 表示不动；</li>
 *   <li>{@code groupKey} 一旦发布禁止改名（{@link com.lanprojects.fitcoach.exercise.service.MuscleGroupService#update}
 *       不会读取 patch 的 groupKey 字段，这里只在创建时用）。</li>
 * </ul>
 */
@Data
public class AdminMuscleGroupRequest {

    /** 全大写下划线，例：CHEST。一旦发布禁止改名 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$",
            message = "groupKey 必须是大写字母开头、含大写字母/数字/下划线、长度 2-32",
            groups = {OnCreate.class})
    @NotBlank(groups = OnCreate.class)
    private String groupKey;

    @NotBlank(groups = OnCreate.class)
    @Size(max = 64)
    private String displayName;

    @Size(max = 8)
    private String emoji;

    @Size(max = 255)
    private String description;

    /** hex 颜色，例 "#FF5722" */
    @Size(max = 16)
    private String color;

    private Integer sortOrder;

    /** 默认 true */
    private Boolean enabled;

    /** 转换为新建用的 MuscleGroupEntity 实体（仅创建时使用） */
    public MuscleGroupEntity toCreateEntity() {
        MuscleGroupEntity g = new MuscleGroupEntity();
        g.setGroupKey(groupKey);
        g.setDisplayName(displayName);
        g.setEmoji(emoji);
        g.setDescription(description);
        g.setColor(color);
        g.setSortOrder(sortOrder != null ? sortOrder : 0);
        g.setEnabled(enabled != null ? enabled : Boolean.TRUE);
        return g;
    }

    /**
     * 把 PATCH 请求里非 null 的字段叠加到现有实体上（实际更新由
     * {@link com.lanprojects.fitcoach.exercise.service.MuscleGroupService#update} 完成）。
     */
    public MuscleGroupEntity toPatchEntity() {
        MuscleGroupEntity patch = new MuscleGroupEntity();
        // groupKey 不允许更新，故不设置
        patch.setDisplayName(displayName);
        patch.setEmoji(emoji);
        patch.setDescription(description);
        patch.setColor(color);
        patch.setSortOrder(sortOrder);
        patch.setEnabled(enabled);
        return patch;
    }

    /** 仅创建时校验 */
    public interface OnCreate {}
}
