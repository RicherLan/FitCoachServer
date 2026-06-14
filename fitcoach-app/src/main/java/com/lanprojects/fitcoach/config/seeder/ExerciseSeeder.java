package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 健身动作播种。
 *
 * <p>数据与 RN 客户端 {@code FitCoachRN/src/training/constants/exercises.ts} 完全对齐 ——
 * 客户端有 Analyzer 实现的所有动作都在这里有一条 seed。
 *
 * <p><b>免费/付费策略</b>：每个肌群保留若干个免费动作，其余默认付费。
 * 运营可在 admin 后台覆盖：
 * <ul>
 *   <li>CHEST: PUSH_UP / DUMBBELL_BENCH_PRESS 免费</li>
 *   <li>BACK: BENT_OVER_ROW / LAT_PULLDOWN 免费</li>
 *   <li>LEGS: SQUAT / BARBELL_BACK_SQUAT 免费</li>
 *   <li>SHOULDERS: LATERAL_RAISE / OVERHEAD_PRESS 免费</li>
 *   <li>ARMS: BICEP_CURL / BARBELL_CURL 免费</li>
 * </ul>
 *
 * <p><b>sortOrder 分段</b>（每组步长 10，便于将来插入）：
 * <pre>
 *   LEGS      100-199
 *   ARMS      200-299
 *   CHEST     300-399
 *   SHOULDERS 400-499
 *   BACK      500-599
 * </pre>
 *
 * <p>{@code cameraSetupJson} 是简单 JSON 字符串：server 不解析，
 * 客户端按 {@code CameraSetup} 类型反序列化。
 *
 * <p><b>幂等</b>：按 {@code exercise_key} 检测，已存在跳过；
 * i18n 字段始终由 {@link ExerciseI18n} 提供，新增动作时一起写入。
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

        // ============================================================
        // LEGS（100-199）
        // ============================================================
        inserted += ensure("SQUAT", "深蹲", "锻炼下肢力量的基础复合动作",
                "股四头肌 · 臀大肌 · 腘绳肌", "🏋", "LEGS", true, 100,
                cameraSetup("FRONT", 2.0, "FULL_BODY",
                        "确保从头到脚都在画面中", "手机与腰部同高效果最佳"));
        inserted += ensure("LUNGE", "弓步蹲", "锻炼单侧腿部力量与平衡性的复合动作",
                "股四头肌 · 臀大肌 · 腘绳肌", "🦿", "LEGS", false, 110,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以检测膝盖角度", "确保从头到脚都在画面中"));
        inserted += ensure("BARBELL_BACK_SQUAT", "杠铃后蹲", "下肢力量训练的王牌复合动作",
                "股四头肌 · 臀大肌 · 腘绳肌 · 核心", "🏋", "LEGS", true, 120,
                cameraSetup("SIDE", 2.5, "FULL_BODY",
                        "侧面拍摄以同时检测蹲深与脊柱中立",
                        "杠铃在斜方肌上沿，画面要拍到整根杠铃",
                        "手机与髋部同高效果最佳"));
        inserted += ensure("LEG_PRESS", "腿举", "孤立训练下肢的器械动作",
                "股四头肌 · 臀大肌", "🦵", "LEGS", false, 130,
                cameraSetup("SIDE", 2.0, "LOWER_BODY",
                        "侧面拍摄以检测膝关节弯曲幅度",
                        "保持膝盖与脚尖方向一致"));
        inserted += ensure("HIP_THRUST", "臀桥（杠铃臀冲）", "孤立训练臀大肌的黄金动作",
                "臀大肌 · 腘绳肌", "🍑", "LEGS", false, 140,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以检测顶峰髋伸角度",
                        "肩部支撑在长凳边缘，画面包含肩到膝盖整段"));

        // ============================================================
        // ARMS（200-299）
        // ============================================================
        inserted += ensure("BICEP_CURL", "哑铃弯举", "针对肱二头肌的经典孤立动作",
                "肱二头肌 · 前臂", "💪", "ARMS", true, 200,
                cameraSetup("ANGLE_45", 1.5, "UPPER_BODY",
                        "斜前方放置可以避免手臂遮挡",
                        "确保手臂弯举轨迹清晰可见",
                        "手机与胸部同高效果最佳"));
        inserted += ensure("OVERHEAD_TRICEP_EXTENSION", "过头臂屈伸", "针对肱三头肌的孤立训练动作",
                "肱三头肌", "💪", "ARMS", false, 210,
                cameraSetup("SIDE", 1.5, "UPPER_BODY",
                        "侧面拍摄以检查肘部轨迹", "确保手臂完整可见"));
        inserted += ensure("BARBELL_CURL", "杠铃弯举", "肱二头肌力量训练首选",
                "肱二头肌 · 前臂", "💪", "ARMS", true, 220,
                cameraSetup("ANGLE_45", 1.5, "UPPER_BODY",
                        "斜前方拍摄可同时看到两侧手臂轨迹",
                        "保持上臂贴近身体，不要借力摆动"));
        inserted += ensure("CABLE_PUSHDOWN", "绳索下压", "针对肱三头肌的经典孤立动作",
                "肱三头肌", "💪", "ARMS", false, 230,
                cameraSetup("SIDE", 1.5, "UPPER_BODY",
                        "侧面拍摄以检查肘部是否固定",
                        "上臂垂直于地面，仅小臂运动"));
        inserted += ensure("SKULL_CRUSHER", "仰卧臂屈伸（窄距）", "孤立训练肱三头肌长头的动作",
                "肱三头肌（长头）", "💀", "ARMS", false, 240,
                cameraSetup("SIDE", 1.8, "UPPER_BODY",
                        "侧面拍摄以观察小臂下降轨迹",
                        "上臂保持垂直地面，肘部不外开"));

        // ============================================================
        // CHEST（300-399）
        // ============================================================
        inserted += ensure("PUSH_UP", "俯卧撑", "经典上肢推类自重训练动作",
                "胸大肌 · 三角肌前束 · 肱三头肌", "🤸", "CHEST", true, 300,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "从侧面拍摄以检查身体直线性", "手机放在地面稍高位置"));
        inserted += ensure("WIDE_PUSH_UP", "宽距俯卧撑", "更强调胸大肌外侧的俯卧撑变式",
                "胸大肌外侧 · 三角肌前束 · 肱三头肌", "🤸", "CHEST", false, 310,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "从侧面拍摄以检查身体直线性", "双手间距约为肩宽的 1.5 倍"));
        inserted += ensure("BARBELL_BENCH_PRESS", "杠铃卧推", "上肢推类力量训练的王牌动作",
                "胸大肌 · 三角肌前束 · 肱三头肌", "🏋", "CHEST", false, 320,
                cameraSetup("SIDE", 2.5, "UPPER_BODY",
                        "侧面拍摄以同时检测下放深度与杠铃轨迹",
                        "手机与胸部同高效果最佳"));
        inserted += ensure("INCLINE_BARBELL_BENCH_PRESS", "上斜杠铃卧推", "更强调胸大肌上束的卧推变式",
                "胸大肌上束 · 三角肌前束", "🏋", "CHEST", false, 330,
                cameraSetup("SIDE", 2.5, "UPPER_BODY",
                        "侧面拍摄以同时检测下放深度与杠铃轨迹",
                        "卧推凳调整到 30-45 度"));
        inserted += ensure("DUMBBELL_BENCH_PRESS", "哑铃卧推", "卧推中的经典哑铃变式，胸肌发力更聚焦",
                "胸大肌 · 三角肌前束 · 肱三头肌", "💪", "CHEST", true, 340,
                cameraSetup("SIDE", 2.0, "UPPER_BODY",
                        "侧面拍摄以同时检测下放深度",
                        "哑铃下放到与胸口同高"));
        inserted += ensure("INCLINE_DUMBBELL_BENCH_PRESS", "上斜哑铃卧推", "上胸塑形动作，比杠铃版稳定性需求更高",
                "胸大肌上束", "💪", "CHEST", false, 350,
                cameraSetup("SIDE", 2.0, "UPPER_BODY",
                        "侧面拍摄以同时检测下放深度",
                        "卧推凳 30-45 度"));
        inserted += ensure("CHEST_FLY_MACHINE", "蝴蝶机夹胸", "孤立胸大肌中缝的器械动作",
                "胸大肌（中缝）", "🦋", "CHEST", false, 360,
                cameraSetup("FRONT", 2.0, "UPPER_BODY",
                        "正面拍摄以检测双臂对称性与开合幅度",
                        "肘部微弯并固定，仅肩水平内收"));

        // ============================================================
        // SHOULDERS（400-499）
        // ============================================================
        inserted += ensure("LATERAL_RAISE", "侧平举", "针对三角肌中束的经典孤立动作",
                "三角肌中束", "🙌", "SHOULDERS", true, 400,
                cameraSetup("FRONT", 1.8, "UPPER_BODY",
                        "正面拍摄以检查手臂抬起高度", "确保双臂完整可见"));
        inserted += ensure("OVERHEAD_PRESS", "站姿杠铃肩推", "上肢推类的核心复合动作",
                "三角肌前束 · 三角肌中束 · 肱三头肌", "🏋", "SHOULDERS", true, 410,
                cameraSetup("FRONT", 2.0, "UPPER_BODY",
                        "正面拍摄以观察推举对称性",
                        "杠铃推到完全锁定"));
        inserted += ensure("SEATED_DUMBBELL_PRESS", "坐姿哑铃肩推", "孤立训练肩部前/中束的稳定版本",
                "三角肌前束 · 三角肌中束", "💪", "SHOULDERS", false, 420,
                cameraSetup("FRONT", 1.8, "UPPER_BODY",
                        "正面拍摄以观察两侧高度一致",
                        "坐姿背贴靠垫，避免借力"));
        inserted += ensure("FRONT_RAISE", "前平举", "针对三角肌前束的孤立动作",
                "三角肌前束", "🙌", "SHOULDERS", false, 430,
                cameraSetup("SIDE", 1.5, "UPPER_BODY",
                        "侧面拍摄以检测手臂抬起角度",
                        "手臂保持基本伸直，不要超过水平线太多"));

        // ============================================================
        // BACK（500-599）
        // ============================================================
        inserted += ensure("BENT_OVER_ROW", "俯身划船", "针对背阔肌的经典复合拉类动作",
                "背阔肌 · 菱形肌 · 肱二头肌", "🚣", "BACK", true, 500,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以检查俯身角度", "确保上半身和手臂轨迹清晰可见"));
        inserted += ensure("LAT_PULLDOWN", "高位下拉", "背阔肌训练入门首选器械动作",
                "背阔肌 · 大圆肌 · 肱二头肌", "🚣", "BACK", true, 510,
                cameraSetup("FRONT", 2.0, "UPPER_BODY",
                        "正面拍摄以检测下拉位置（拉到锁骨上方）",
                        "保持躯干微微后仰即可，不要大幅借力"));
        inserted += ensure("SEATED_CABLE_ROW", "坐姿绳索划船", "厚度训练的经典动作，对腰背更友好",
                "背阔肌 · 菱形肌 · 斜方肌中下", "🚣", "BACK", false, 520,
                cameraSetup("SIDE", 2.0, "UPPER_BODY",
                        "侧面拍摄以同时检测拉到位与躯干角度",
                        "拉到肚脐高度，肘部贴近身体"));
        inserted += ensure("BARBELL_ROW", "杠铃划船", "经典背部厚度训练复合动作",
                "背阔肌 · 菱形肌 · 后三角肌", "🏋", "BACK", false, 530,
                cameraSetup("SIDE", 2.0, "FULL_BODY",
                        "侧面拍摄以同时检测俯身角度（约 45°）与拉到胸/腹高度",
                        "保持脊柱中立，不要弓背"));
        inserted += ensure("PULL_UP", "引体向上", "上肢自重训练王者动作",
                "背阔肌 · 大圆肌 · 肱二头肌", "🧗", "BACK", false, 540,
                cameraSetup("FRONT", 2.5, "FULL_BODY",
                        "正面拍摄以确保两侧用力均匀",
                        "下颌过杠为一次完整动作"));

        if (inserted > 0) {
            log.info("[seeder] 健身动作初始化完成，新增 {} 项", inserted);
        }
    }

    /**
     * 按 exercise_key 幂等插入。
     * 已存在直接跳过，由 admin 后台负责后续运营修改。
     */
    private int ensure(String key, String displayName, String description,
                       String muscles, String emoji, String groupKey, boolean isFree,
                       int sortOrder, String cameraJson) {
        if (exerciseRepository.findByExerciseKey(key).isPresent()) {
            return 0;
        }
        Exercise e = new Exercise();
        e.setExerciseKey(key);
        e.setDisplayName(displayName);
        e.setDisplayNameI18n(ExerciseI18n.displayName(key));
        e.setDescription(description);
        e.setDescriptionI18n(ExerciseI18n.description(key));
        e.setMuscles(muscles);
        e.setMusclesI18n(ExerciseI18n.muscles(key));
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
     * 手写一个简单的 CameraSetup JSON，避免在播种期引入 Jackson 依赖。
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
