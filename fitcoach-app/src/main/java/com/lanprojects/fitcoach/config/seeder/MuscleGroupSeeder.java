package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import com.lanprojects.fitcoach.exercise.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 肌群播种 — 共 12 个肌群，来自两套数据源：
 * <ul>
 *   <li><b>原 7 个</b>（CHEST/BACK/LEGS/SHOULDERS/ARMS/CORE/FULL_BODY）：搬迁自 RN
 *       {@code FitCoachRN/src/training/constants/exercises.ts} 的 MUSCLE_GROUPS，
 *       以及历史 Java 枚举 {@code MuscleGroup} 中预留的 CORE / FULL_BODY。
 *       用于 AI 实时识别动作（{@code exercise} 表）的分类，保留向后兼容。</li>
 *   <li><b>新增 5 个</b>（BICEPS/TRICEPS/CALVES/FOREARM/CARDIO）：训练记录模块
 *       （{@code fitcoach-training-record}）专用的细分肌群，与 ARMS / LEGS 父肌群共存。
 *       未来 admin 端可决定是否把 AI 动作也迁到细分肌群。</li>
 * </ul>
 *
 * <p>{@code @Order(35)}：在 {@link MembershipPlanSeeder}（30）之后、{@link ExerciseSeeder}（40）
 * 及 {@code TrainingExerciseSeeder}（45）之前，确保 muscle_group 表先于动作数据被填充——
 * 引用 muscle_group.group_key 的两张动作表（exercise / training_exercise）创建时一定有效。
 *
 * <p><b>幂等</b>：按 group_key 检测，已存在跳过，不覆盖运营在 admin 端的修改。
 *
 * <p><b>sortOrder 设计</b>：父肌群按 10/20/30/... 等距排，细分肌群插入到对应父肌群附近
 * （CALVES=35 紧跟 LEGS=30；BICEPS/TRICEPS/FOREARM 紧跟 ARMS=50）；CARDIO=80 排末尾。
 *
 * <p><b>颜色</b>：原 7 个保持与 RN 端完全一致；新增 5 个用相近色系区分（紫系=手臂细分，绿=小腿，蓝=有氧）。
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
        // ====== 原 7 个：与 RN MUSCLE_GROUPS / MUSCLE_GROUP_ORDER 对齐，AI 动作沿用 ======
        inserted += ensure("CHEST",     "胸部训练", "🔥",   "胸大肌 · 三角肌前束 · 肱三头肌", "#FF6B6B", 10);
        inserted += ensure("BACK",      "背部训练", "🎯",   "背阔肌 · 斜方肌 · 菱形肌",       "#5B8DEF", 20);
        inserted += ensure("LEGS",      "腿部训练", "🦵",   "股四头肌 · 臀大肌 · 腘绳肌",     "#2ED8A3", 30);
        // CALVES（小腿）细分肌群，紧跟 LEGS
        inserted += ensure("CALVES",    "小腿训练", "🦵",   "腓肠肌 · 比目鱼肌",             "#7CB342", 35);
        inserted += ensure("SHOULDERS", "肩膀训练", "🏋️",   "三角肌前束 · 中束 · 后束",       "#FFB347", 40);
        inserted += ensure("ARMS",      "手臂训练", "💪",   "肱二头肌 · 肱三头肌 · 前臂",     "#A78BFA", 50);
        // 手臂细分肌群（训练记录专用），紧跟 ARMS
        inserted += ensure("BICEPS",    "二头训练", "💪",   "肱二头肌 · 肱肌",               "#B57EDC", 51);
        inserted += ensure("TRICEPS",   "三头训练", "💪",   "肱三头肌（长头 · 外侧头 · 内侧头）", "#9C7FD6", 52);
        inserted += ensure("FOREARM",   "前臂训练", "✊",   "腕屈肌群 · 腕伸肌群 · 握力",      "#8E63B5", 53);
        // 枚举中预留但 RN 未上线的两个肌群（无动作时由"客户端空类目自动隐藏"逻辑兜底）
        inserted += ensure("CORE",      "核心训练", "🌀",   "腹直肌 · 腹外斜肌 · 竖脊肌",     "#FF9D5C", 60);
        inserted += ensure("FULL_BODY", "全身复合", "🌐",   "多关节复合训练",                "#9CA3AF", 70);
        // 有氧排末尾，与力量肌群在视觉上自然分隔
        inserted += ensure("CARDIO",    "有氧训练", "🏃",   "有氧耐力 · 心肺功能",            "#5BB1E0", 80);

        if (inserted > 0) {
            log.info("[seeder] 肌群初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensure(String key, String displayName, String emoji,
                       String description, String color, int sortOrder) {
        if (muscleGroupRepository.existsByGroupKey(key)) {
            return 0;
        }
        MuscleGroupEntity g = new MuscleGroupEntity();
        g.setGroupKey(key);
        g.setDisplayName(displayName);
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
