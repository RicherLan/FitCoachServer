package com.lanprojects.fitcoach.trainingrecord.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 训练动作库（用户手动录入训练记录时从此表选择动作）。
 *
 * <p><b>与 fitcoach-exercise 模块的 {@code Exercise} 完全独立</b>：
 * <ul>
 *   <li>{@code Exercise}（{@code exercise} 表）：AI 实时识别动作（深蹲/俯卧撑/二头弯举等 8 个），
 *       带 cameraSetupJson 让客户端做相机姿态校准；</li>
 *   <li>{@code TrainingExercise}（{@code training_exercise} 表）：通用训练动作字典（86+），
 *       覆盖力量 / 有氧 / 自重，用户在「记录」Tab 下手动写训练日志时从此选择。</li>
 * </ul>
 * 两表的 {@code exerciseKey} <b>允许重名</b>（如 BICEP_CURL 在 AI 识别和手动记录都存在），
 * MVP 不做映射关联；将来如要"AI 识别 → 自动写入手动记录"再补 mapping 表。
 *
 * <p><b>共享肌群字典</b>：{@link #muscleGroup} 软外键引用 {@code muscle_group.group_key}
 * （由 fitcoach-exercise 模块的 MuscleGroupEntity 维护，TrainingExercise 与 Exercise 共享同一份分类）。
 *
 * <p><b>MVP 不支持自定义动作</b>：{@link #isCustom} 字段已预留但暂时不开放给用户写入，
 * 全部种子动作 {@code isCustom = false / userId = null}。将来开放自定义时直接复用本表。
 *
 * <p><b>免费/付费策略</b>：MVP 决定全部动作免费（不像 Exercise 表有 isFree 字段）。
 * 将来若需要付费动作再 alter table 加 is_free 列。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training_exercise", indexes = {
        // 同一所有者（userId）下 exerciseKey 唯一：内置（userId=NULL）走全局唯一，自定义按用户隔离
        @Index(name = "uk_training_exercise_owner_key", columnList = "user_id, exercise_key", unique = true),
        @Index(name = "idx_training_exercise_enabled", columnList = "enabled"),
        @Index(name = "idx_training_exercise_muscle_group", columnList = "muscle_group"),
        @Index(name = "idx_training_exercise_user_id", columnList = "user_id"),
        @Index(name = "idx_training_exercise_equipment", columnList = "equipment")
})
public class TrainingExercise extends BaseEntity {

    /**
     * 动作业务 key（客户端 / server 内部统一标识符），全大写下划线。
     * <p>例：BARBELL_BENCH_PRESS / DUMBBELL_ROW / RUNNING。
     * <p>跟 {@link #userId} 联合唯一：内置动作（userId=null）必须全局唯一；
     * 同一用户自定义动作的 key 不能重；不同用户之间允许同 key。
     */
    @Column(name = "exercise_key", nullable = false, length = 64)
    private String exerciseKey;

    /**
     * 显示名称（中文/可国际化），用户端列表展示用。例："杠铃卧推" "哑铃划船"。
     */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /**
     * 描述 / 训练要点（admin 可选填，用户端在动作详情或长按 tooltip 展示）。
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 表情符号（动作选择器卡片装饰用，与肌群 emoji 互补）。
     *
     * <p><b>渲染优先级（v2）</b>：客户端按 {@code iconUrl > emoji > 本地兜底 emoji} 顺序展示。
     * 当 {@link #iconUrl} 非空时，emoji 仅作为 url 加载失败 / 离线缓存空的二级兜底。
     */
    @Column(name = "emoji", length = 8)
    private String emoji;

    /**
     * 自定义图标 URL（admin 上传 PNG/JPG/WebP，存 server 静态资源目录，返回相对路径如
     * {@code /static/trainingexercise-icon/BARBELL_BENCH_PRESS/xxx.png}）。
     *
     * <p>设计原因：Unicode emoji 字符集对健身动作（飞鸟 / 划船 / 弯举 / 提踵 / 卷腹 ...）
     * 大量没有真实对应符号，硬凑 emoji 反而错位；本字段允许运营在 admin 后台上传精准小图，
     * 客户端**优先**渲染。为空时回落到 {@link #emoji} → 本地内置兜底 emoji。
     *
     * <p>客户端拿到的是相对路径，由 axios 实例 baseURL 拼接成完整 URL，跨网络环境通用。
     */
    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    /**
     * 肌群分类 key（软外键引用 {@code muscle_group.group_key}）。
     * <p>例：CHEST / BICEPS / CARDIO。admin 写入时由 Service 层校验该 key 存在于肌群字典中。
     */
    @Column(name = "muscle_group", nullable = false, length = 32)
    private String muscleGroup;

    /**
     * 器械类型。当前允许：BARBELL / DUMBBELL / MACHINE / BODYWEIGHT / CABLE / CARDIO。
     * <p>用 String 而非 enum，方便 admin 后续扩展（如 KETTLEBELL / RESISTANCE_BAND）无需改代码。
     * <p>客户端按值渲染 chip 标签 + 颜色（绿色器械 / 灰色自重 / 蓝色有氧）。
     */
    @Column(name = "equipment", nullable = false, length = 32)
    private String equipment;

    /**
     * 是否用户自定义。
     * <ul>
     *   <li>{@code false}（默认）：admin 维护的内置动作，{@link #userId} = null；</li>
     *   <li>{@code true}：用户自定义动作，{@link #userId} 非空，只对该用户可见。</li>
     * </ul>
     * <b>MVP 全部为 false</b>（不开放自定义入口）；字段保留是为将来 P1 开放自定义时不动 schema。
     */
    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom = false;

    /**
     * 自定义动作的所有者用户 id；内置动作为 null。
     * <p>用户端列表查询时 WHERE (user_id IS NULL OR user_id = :currentUser) 实现"内置 + 自己自定义"合并。
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 排序权重（小 → 前），同肌群内按此排。
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 是否启用（admin 下架某个动作设 false，用户端列表不再返回；已写入历史训练记录中的引用不受影响 —— 训练记录本身存的是 key + 名字快照）。
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
