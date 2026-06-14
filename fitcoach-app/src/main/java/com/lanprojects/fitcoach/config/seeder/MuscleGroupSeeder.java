package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import com.lanprojects.fitcoach.exercise.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 肌群播种 — 共 12 个肌群，与 RN 客户端 {@code MUSCLE_GROUPS / MUSCLE_GROUP_ORDER} 对齐。
 *
 * <p><b>分类</b>：
 * <ul>
 *   <li><b>AI 实时动作分类</b>（{@code exercise} 表）：CHEST / BACK / LEGS / SHOULDERS / ARMS / CORE / FULL_BODY</li>
 *   <li><b>训练记录细分</b>（{@code training_exercise} 表）：BICEPS / TRICEPS / CALVES / FOREARM</li>
 *   <li><b>有氧</b>：CARDIO</li>
 * </ul>
 *
 * <p>{@code @Order(35)}：在 {@link MembershipPlanSeeder}（30）之后、{@link ExerciseSeeder}（40）
 * 及 {@code TrainingExerciseSeeder}（45）之前，确保 muscle_group 表先于动作数据被填充 ——
 * 引用 muscle_group.group_key 的两张动作表（exercise / training_exercise）创建时一定有效。
 *
 * <p><b>幂等</b>：按 group_key 检测，已存在跳过；i18n 字段由 {@link ExerciseI18n#muscleGroupName} 提供。
 *
 * <p><b>sortOrder 设计</b>：父肌群每 10 一档，细分肌群在父肌群后顺延；CARDIO 排末尾与力量类自然分隔。
 *
 * <p><b>颜色</b>：父肌群对应 RN 端配色；手臂细分用紫系、小腿绿系、有氧蓝系区分。
 */
@Slf4j
@Order(35)
@Component
@RequiredArgsConstructor
public class MuscleGroupSeeder implements CommandLineRunner {

    private final MuscleGroupRepository muscleGroupRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        // 有氧：放在最前面（入门门槛最低、记录频率最高，用户打开选择器优先看到）
        inserted += ensure("CARDIO",    "有氧训练", "🏃",   "有氧耐力 · 心肺功能",            "#5BB1E0", 5);
        // AI 动作分类
        inserted += ensure("CHEST",     "胸部训练", "🔥",   "胸大肌 · 三角肌前束 · 肱三头肌", "#FF6B6B", 10);
        inserted += ensure("BACK",      "背部训练", "🎯",   "背阔肌 · 斜方肌 · 菱形肌",       "#5B8DEF", 20);
        inserted += ensure("LEGS",      "腿部训练", "🦵",   "股四头肌 · 臀大肌 · 腘绳肌",     "#2ED8A3", 30);
        inserted += ensure("CALVES",    "小腿训练", "🦵",   "腓肠肌 · 比目鱼肌",             "#7CB342", 35);
        inserted += ensure("SHOULDERS", "肩膀训练", "🏋️",   "三角肌前束 · 中束 · 后束",       "#FFB347", 40);
        inserted += ensure("ARMS",      "手臂训练", "💪",   "肱二头肌 · 肱三头肌 · 前臂",     "#A78BFA", 50);
        // 手臂细分（训练记录用）
        inserted += ensure("BICEPS",    "二头训练", "💪",   "肱二头肌 · 肱肌",               "#B57EDC", 51);
        inserted += ensure("TRICEPS",   "三头训练", "💪",   "肱三头肌（长头 · 外侧头 · 内侧头）", "#9C7FD6", 52);
        inserted += ensure("FOREARM",   "前臂训练", "✊",   "腕屈肌群 · 腕伸肌群 · 握力",      "#8E63B5", 53);
        // 核心 / 全身
        inserted += ensure("CORE",      "核心训练", "🌀",   "腹直肌 · 腹外斜肌 · 竖脊肌",     "#FF9D5C", 60);
        inserted += ensure("FULL_BODY", "全身复合", "🌐",   "多关节复合训练",                "#9CA3AF", 70);

        if (inserted > 0) {
            log.info("[seeder] 肌群初始化完成，新增 {} 项", inserted);
        }
    }

    /**
     * 按 group_key 幂等插入。
     * 已存在直接跳过；i18n displayName 由 {@link ExerciseI18n#muscleGroupName} 提供 8 语言 JSON。
     */
    private int ensure(String key, String displayName, String emoji,
                       String description, String color, int sortOrder) {
        if (muscleGroupRepository.findByGroupKey(key).isPresent()) {
            return 0;
        }
        MuscleGroupEntity g = new MuscleGroupEntity();
        g.setGroupKey(key);
        g.setDisplayName(displayName);
        g.setDisplayNameI18n(ExerciseI18n.muscleGroupName(key));
        g.setEmoji(emoji);
        g.setDescription(description);
        g.setColor(color);
        g.setSortOrder(sortOrder);
        g.setEnabled(true);
        muscleGroupRepository.save(g);
        log.info("[seeder] 创建肌群：{} ({}) {}", displayName, key, emoji);
        return 1;
    }
}
