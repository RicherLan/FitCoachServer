package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import com.lanprojects.fitcoach.exercise.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 7 个肌群播种 — 数据搬迁自：
 * <ul>
 *   <li>RN 端 {@code FitCoachRN/src/training/constants/exercises.ts} 的 MUSCLE_GROUPS（5 个：胸/背/腿/肩/臂）；</li>
 *   <li>历史 Java 枚举 {@code MuscleGroup} 中预留但 RN 未实现的 CORE / FULL_BODY 也一并预置（方便后续新增动作直接归类）。</li>
 * </ul>
 *
 * <p>{@code @Order(35)}：在 {@link MembershipPlanSeeder}（30）之后、{@link ExerciseSeeder}（40）之前，
 * 确保 muscle_group 表先于 Exercise 数据被填充——这样将来 S3 把 Exercise.muscleGroup 改为
 * 软外键引用 muscle_group.group_key 时，引用关系一定有效。
 *
 * <p><b>幂等</b>：按 group_key 检测，已存在跳过，不覆盖运营在 admin 端的修改。
 *
 * <p><b>颜色与 emoji 与 RN 端 MUSCLE_GROUPS 完全一致</b>，迁移后客户端展示无任何视觉差异。
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
        // 与 RN MUSCLE_GROUPS / MUSCLE_GROUP_ORDER 对齐
        inserted += ensure("CHEST",     "胸部训练", "🔥",   "胸大肌 · 三角肌前束 · 肱三头肌", "#FF6B6B", 10);
        inserted += ensure("BACK",      "背部训练", "🎯",   "背阔肌 · 斜方肌 · 菱形肌",       "#5B8DEF", 20);
        inserted += ensure("LEGS",      "腿部训练", "🦵",   "股四头肌 · 臀大肌 · 腘绳肌",     "#2ED8A3", 30);
        inserted += ensure("SHOULDERS", "肩膀训练", "🏋️",   "三角肌前束 · 中束 · 后束",       "#FFB347", 40);
        inserted += ensure("ARMS",      "手臂训练", "💪",   "肱二头肌 · 肱三头肌 · 前臂",     "#A78BFA", 50);
        // 枚举中预留但 RN 未上线的两个肌群（无动作时由"客户端空类目自动隐藏"逻辑兜底）
        inserted += ensure("CORE",      "核心训练", "🌀",   "腹直肌 · 腹外斜肌 · 竖脊肌",     "#FF9D5C", 60);
        inserted += ensure("FULL_BODY", "全身复合", "🌐",   "多关节复合训练",                "#9CA3AF", 70);

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
