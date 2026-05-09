package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 8 个健身动作播种 — 数据搬迁自 RN {@code FitCoachRN/src/training/constants/exercises.ts}。
 *
 * <p><b>免费/付费策略</b>：每个肌群保留 1 个免费动作（满足"每肌群至少 1 个免费"运营规则），
 * 其它默认付费。运营可在 admin 后台后续调整。
 *
 * <ul>
 *   <li>CHEST: PUSH_UP 免费 / WIDE_PUSH_UP 付费</li>
 *   <li>BACK: BENT_OVER_ROW 免费</li>
 *   <li>LEGS: SQUAT 免费 / LUNGE 付费</li>
 *   <li>SHOULDERS: LATERAL_RAISE 免费</li>
 *   <li>ARMS: BICEP_CURL 免费 / OVERHEAD_TRICEP_EXTENSION 付费</li>
 * </ul>
 *
 * <p>{@code cameraSetupJson} 是简单 JSON 字符串，server 不解析、客户端按原 CameraSetup 类型反序列化。
 * <p><b>幂等</b>：按 exercise_key 检测，已存在跳过。
 */
@Slf4j
@Order(40)
@Component
@RequiredArgsConstructor
public class ExerciseSeeder implements CommandLineRunner {

    private final ExerciseRepository exerciseRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        inserted += ensure("SQUAT", "深蹲", "锻炼下肢力量的基础复合动作",
                "股四头肌 · 臀大肌 · 腘绳肌", "🏋", "LEGS", true, 10,
                cameraSetup("FRONT", 2.0, "FULL_BODY",
                        "确保从头到脚都在画面中", "手机与腰部同高效果最佳"));
        inserted += ensure("BICEP_CURL", "哑铃弯举", "针对肱二头肌的经典孤立动作",
                "肱二头肌 · 前臂", "💪", "ARMS", true, 20,
                cameraSetup("ANGLE_45", 1.5, "UPPER_BODY",
                        "斜前方放置可以避免手臂遮挡",
                        "确保手臂弯举轨迹清晰可见",
                        "手机与胸部同高效果最佳"));
        inserted += ensure("PUSH_UP", "俯卧撑", "经典上肢推类自重训练动作",
                "胸大肌 · 三角肌前束 · 肱三头肌", "🤸", "CHEST", true, 30,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "从侧面拍摄以检查身体直线性", "手机放在地面稍高位置"));
        inserted += ensure("WIDE_PUSH_UP", "宽距俯卧撑", "更强调胸大肌外侧的俯卧撑变式",
                "胸大肌外侧 · 三角肌前束 · 肱三头肌", "🤸", "CHEST", false, 31,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "从侧面拍摄以检查身体直线性", "双手间距约为肩宽的 1.5 倍"));
        inserted += ensure("LUNGE", "弓步蹲", "锻炼单侧腿部力量与平衡性的复合动作",
                "股四头肌 · 臀大肌 · 腘绳肌", "🦿", "LEGS", false, 11,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以检测膝盖角度", "确保从头到脚都在画面中"));
        inserted += ensure("LATERAL_RAISE", "侧平举", "针对三角肌中束的经典孤立动作",
                "三角肌中束", "🙌", "SHOULDERS", true, 40,
                cameraSetup("FRONT", 1.8, "UPPER_BODY",
                        "正面拍摄以检查手臂抬起高度", "确保双臂完整可见"));
        inserted += ensure("BENT_OVER_ROW", "俯身划船", "针对背阔肌的经典复合拉类动作",
                "背阔肌 · 菱形肌 · 肱二头肌", "🚣", "BACK", true, 50,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以检查俯身角度", "确保上半身和手臂轨迹清晰可见"));
        inserted += ensure("OVERHEAD_TRICEP_EXTENSION", "过头臂屈伸", "针对肱三头肌的孤立训练动作",
                "肱三头肌", "💪", "ARMS", false, 21,
                cameraSetup("SIDE", 1.5, "UPPER_BODY",
                        "侧面拍摄以检查肘部轨迹", "确保手臂完整可见"));

        if (inserted > 0) {
            log.info("[seeder] 健身动作初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensure(String key, String displayName, String description,
                       String muscles, String emoji, String groupKey, boolean isFree,
                       int sortOrder, String cameraJson) {
        if (exerciseRepository.findByExerciseKey(key).isPresent()) {
            return 0;
        }
        Exercise e = new Exercise();
        e.setExerciseKey(key);
        e.setDisplayName(displayName);
        e.setDescription(description);
        e.setMuscles(muscles);
        e.setEmoji(emoji);
        e.setMuscleGroup(groupKey);
        e.setIsFree(isFree);
        e.setSortOrder(sortOrder);
        e.setEnabled(true);
        e.setCameraSetupJson(cameraJson);
        exerciseRepository.save(e);
        log.info("[seeder] 创建动作：{} ({}) {} {}",
                displayName, key, groupKey, isFree ? "[免费]" : "[付费]");
        return 1;
    }

    /**
     * 手写一个简单的 CameraSetup JSON。避免引入 Jackson 依赖在播种期。
     * 字段对应 RN 端 {@code CameraSetup} 类型：position / distanceMeters / frameTarget / placementTips。
     */
    private static String cameraSetup(String position, double distanceMeters,
                                      String frameTarget, String... placementTips) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"position\":\"").append(position).append("\",");
        sb.append("\"distanceMeters\":").append(distanceMeters).append(",");
        sb.append("\"frameTarget\":\"").append(frameTarget).append("\",");
        sb.append("\"placementTips\":[");
        for (int i = 0; i < placementTips.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(placementTips[i].replace("\"", "\\\"")).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }
}
