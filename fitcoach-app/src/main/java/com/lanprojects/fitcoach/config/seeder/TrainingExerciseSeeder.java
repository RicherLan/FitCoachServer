package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import com.lanprojects.fitcoach.trainingrecord.repository.TrainingExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 训练动作库初始播种 — 86 个内置动作。
 *
 * <p><b>来源</b>：综合 Keep / 练练 / 训记 / 开练 等主流健身 App 的高频通用动作，
 * 按肌群分布（PRD §3）：胸 14 · 背 13 · 肩 9 · 二头 7 · 三头 8 · 腿 13 · 小腿 3 · 核心 8 · 前臂 3 · 有氧 8。
 *
 * <p><b>@Order(45)</b>：在 {@link MuscleGroupSeeder}（35）之后、所有用户数据相关之前，
 * 确保播种时引用的肌群 key（CHEST / BICEPS / CARDIO ...）一定存在于 muscle_group 表。
 *
 * <p><b>幂等</b>：按 exerciseKey + userId IS NULL 检测，已存在跳过，不覆盖运营在 admin 端的修改。
 * 这意味着首次启动写入 86 条；以后再启动 0 条新增；运营改了显示名 / emoji 后再重启不会被覆盖。
 *
 * <p><b>动作 key 与 fitcoach-exercise 的关系</b>：本表的 key 与 {@code exercise} 表的 key 相互独立，
 * 即使重名（如 BARBELL_CURL / DUMBBELL_BENCH_PRESS）也不冲突——两套数据走两条业务链路。
 *
 * <p><b>器械类型 (equipment)</b>：BARBELL / DUMBBELL / MACHINE / BODYWEIGHT / CABLE / CARDIO，
 * 与 {@link com.lanprojects.fitcoach.trainingrecord.service.TrainingExerciseService#ALLOWED_EQUIPMENTS} 白名单一致。
 *
 * <p><b>sortOrder 设计</b>：组内步长 10（10,20,30...），方便运营在 admin 端用 15/25 等中间值插队。
 */
@Slf4j
@Order(45)
@Component
@RequiredArgsConstructor
public class TrainingExerciseSeeder implements CommandLineRunner {

    private final TrainingExerciseRepository trainingExerciseRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;

        // ============ 胸 CHEST · 14 个 ============
        inserted += ensure("BARBELL_BENCH_PRESS",         "杠铃卧推",       "🏋️", "CHEST", "BARBELL",    "胸大肌力量训练之王 · 平板凳上水平推举", 10);
        inserted += ensure("INCLINE_BARBELL_BENCH_PRESS", "上斜杠铃卧推",   "🏋️", "CHEST", "BARBELL",    "针对胸大肌上束 · 凳面 30-45°",        20);
        inserted += ensure("DECLINE_BARBELL_BENCH_PRESS", "下斜杠铃卧推",   "🏋️", "CHEST", "BARBELL",    "针对胸大肌下束 · 凳面下倾",          30);
        inserted += ensure("DUMBBELL_BENCH_PRESS",        "哑铃卧推",       "💪", "CHEST", "DUMBBELL",   "活动范围更大 · 增强稳定性",          40);
        inserted += ensure("INCLINE_DUMBBELL_BENCH_PRESS","上斜哑铃卧推",   "💪", "CHEST", "DUMBBELL",   "上胸塑形 · 凳面 30-45°",             50);
        inserted += ensure("DUMBBELL_FLY",                "哑铃飞鸟",       "🦋", "CHEST", "DUMBBELL",   "胸肌孤立训练 · 拉伸感强",            60);
        inserted += ensure("INCLINE_DUMBBELL_FLY",        "上斜哑铃飞鸟",   "🦋", "CHEST", "DUMBBELL",   "上胸孤立 · 凳面上倾",                70);
        inserted += ensure("CHEST_FLY_MACHINE",           "蝴蝶机夹胸",     "⚙️", "CHEST", "MACHINE",    "胸肌孤立 · 轨迹固定适合新手",        80);
        inserted += ensure("CABLE_CROSSOVER",             "龙门架夹胸",     "🔗", "CHEST", "CABLE",      "胸肌持续张力 · 末端挤压感强",        90);
        inserted += ensure("PEC_DECK",                    "坐姿夹胸",       "⚙️", "CHEST", "MACHINE",    "坐姿固定轨迹胸肌孤立训练",          100);
        inserted += ensure("SMITH_BENCH_PRESS",           "史密斯卧推",     "⚙️", "CHEST", "MACHINE",    "杠铃轨迹固定 · 新手友好",           110);
        inserted += ensure("PUSH_UP",                     "俯卧撑",         "🤸", "CHEST", "BODYWEIGHT", "经典自重胸部训练 · 随时可练",       120);
        inserted += ensure("DIAMOND_PUSH_UP",             "钻石俯卧撑",     "💎", "CHEST", "BODYWEIGHT", "双手呈钻石形 · 内胸 + 三头",        130);
        inserted += ensure("DIPS",                        "双杠臂屈伸",     "🤸", "CHEST", "BODYWEIGHT", "胸下束 + 三头 · 身体前倾偏胸",      140);

        // ============ 背 BACK · 13 个 ============
        inserted += ensure("DEADLIFT",            "硬拉",           "🏋️", "BACK", "BARBELL",    "全身复合动作之王 · 背 + 腿 + 核心",   210);
        inserted += ensure("ROMANIAN_DEADLIFT",   "罗马尼亚硬拉",   "🏋️", "BACK", "BARBELL",    "针对腘绳肌和下背 · 微屈膝",          220);
        inserted += ensure("BARBELL_ROW",         "杠铃划船",       "🏋️", "BACK", "BARBELL",    "背阔肌厚度训练 · 俯身姿势",          230);
        inserted += ensure("T_BAR_ROW",           "T 杠划船",       "🏋️", "BACK", "BARBELL",    "中背训练 · 单边支点稳定",            240);
        inserted += ensure("DUMBBELL_ROW",        "哑铃划船",       "💪", "BACK", "DUMBBELL",   "单边背阔肌训练 · 一手扶凳",          250);
        inserted += ensure("SEATED_CABLE_ROW",    "坐姿绳索划船",   "🔗", "BACK", "CABLE",      "持续张力 · 训练背阔肌厚度",          260);
        inserted += ensure("LAT_PULLDOWN",        "高位下拉",       "⚙️", "BACK", "MACHINE",    "背阔宽度训练 · 引体替代品",          270);
        inserted += ensure("PULL_UP",             "引体向上",       "🤸", "BACK", "BODYWEIGHT", "上肢自重之王 · 正握偏背阔",          280);
        inserted += ensure("CHIN_UP",             "反握引体",       "🤸", "BACK", "BODYWEIGHT", "反握更易完成 · 二头参与多",          290);
        inserted += ensure("ASSISTED_PULL_UP",    "辅助引体",       "⚙️", "BACK", "MACHINE",    "机器助力 · 引体进阶训练",            300);
        inserted += ensure("FACE_PULL",           "面拉",           "🔗", "BACK", "CABLE",      "后三角 + 中背 · 改善圆肩",          310);
        inserted += ensure("SHRUG",               "耸肩",           "💪", "BACK", "DUMBBELL",   "斜方肌孤立训练",                     320);
        inserted += ensure("HYPEREXTENSION",      "山羊挺身",       "🤸", "BACK", "BODYWEIGHT", "下背 + 臀部 · 罗马椅",              330);

        // ============ 肩 SHOULDERS · 9 个 ============
        inserted += ensure("OVERHEAD_PRESS",          "站姿杠铃推举", "🏋️", "SHOULDERS", "BARBELL",  "肩部力量动作之王",            410);
        inserted += ensure("SEATED_DUMBBELL_PRESS",   "坐姿哑铃推举", "💪", "SHOULDERS", "DUMBBELL", "稳定性要求低 · 三角肌全束",  420);
        inserted += ensure("ARNOLD_PRESS",            "阿诺德推举",   "💪", "SHOULDERS", "DUMBBELL", "起始反握 · 顶端正握",        430);
        inserted += ensure("SMITH_SHOULDER_PRESS",    "史密斯肩推",   "⚙️", "SHOULDERS", "MACHINE",  "轨迹固定 · 新手友好",        440);
        inserted += ensure("DUMBBELL_LATERAL_RAISE",  "哑铃侧平举",   "💪", "SHOULDERS", "DUMBBELL", "三角肌中束 · 宽肩必练",      450);
        inserted += ensure("CABLE_LATERAL_RAISE",     "绳索侧平举",   "🔗", "SHOULDERS", "CABLE",    "持续张力中束孤立训练",      460);
        inserted += ensure("FRONT_RAISE",             "哑铃前平举",   "💪", "SHOULDERS", "DUMBBELL", "三角肌前束",                470);
        inserted += ensure("REAR_DELT_FLY",           "反向飞鸟",     "💪", "SHOULDERS", "DUMBBELL", "三角肌后束 · 俯身姿势",     480);
        inserted += ensure("UPRIGHT_ROW",             "直立划船",     "🏋️", "SHOULDERS", "BARBELL",  "三角肌中束 + 斜方肌",        490);

        // ============ 二头 BICEPS · 7 个 ============
        inserted += ensure("BARBELL_CURL",          "杠铃弯举",       "🏋️", "BICEPS", "BARBELL",  "二头基础动作 · 大重量训练",   510);
        inserted += ensure("EZ_BAR_CURL",           "EZ 杠弯举",      "🏋️", "BICEPS", "BARBELL",  "弯曲握把 · 手腕更舒适",       520);
        inserted += ensure("DUMBBELL_CURL",         "哑铃弯举",       "💪", "BICEPS", "DUMBBELL", "可单边训练 · 旋臂幅度大",     530);
        inserted += ensure("HAMMER_CURL",           "哑铃锤式弯举",   "🔨", "BICEPS", "DUMBBELL", "锤式握法 · 肱肌发达",         540);
        inserted += ensure("INCLINE_DUMBBELL_CURL", "斜板哑铃弯举",   "💪", "BICEPS", "DUMBBELL", "拉伸感强 · 二头长头训练",     550);
        inserted += ensure("PREACHER_CURL",         "牧师凳弯举",     "⚙️", "BICEPS", "MACHINE",  "孤立训练 · 杜绝借力",         560);
        inserted += ensure("CABLE_CURL",            "绳索弯举",       "🔗", "BICEPS", "CABLE",    "持续张力 · 全程顶峰收缩",     570);

        // ============ 三头 TRICEPS · 8 个 ============
        inserted += ensure("CLOSE_GRIP_BENCH_PRESS",      "窄距卧推",       "🏋️", "TRICEPS", "BARBELL",    "三头复合动作 · 兼顾胸下束", 610);
        inserted += ensure("SKULL_CRUSHER",               "颅骨破碎者",     "🏋️", "TRICEPS", "BARBELL",    "三头孤立 · 仰卧屈伸",       620);
        inserted += ensure("OVERHEAD_DUMBBELL_EXTENSION", "哑铃过顶臂屈伸", "💪", "TRICEPS", "DUMBBELL",   "三头长头训练 · 拉伸感强",   630);
        inserted += ensure("DUMBBELL_KICKBACK",           "哑铃后撤臂屈伸", "💪", "TRICEPS", "DUMBBELL",   "三头外侧头 · 末端发力",     640);
        inserted += ensure("CABLE_PUSHDOWN",              "绳索下压",       "🔗", "TRICEPS", "CABLE",      "三头基础孤立 · 直杆/V 杆",  650);
        inserted += ensure("ROPE_PUSHDOWN",               "绳索下压（绳柄）","🔗", "TRICEPS", "CABLE",      "末端分柄 · 三头外侧训练",   660);
        inserted += ensure("TRICEPS_DIPS",                "三头双杠臂屈伸", "🤸", "TRICEPS", "BODYWEIGHT", "身体直立 · 三头主导",       670);
        inserted += ensure("BENCH_DIPS",                  "凳上臂屈伸",     "🤸", "TRICEPS", "BODYWEIGHT", "凳子撑起 · 三头入门动作",   680);

        // ============ 腿 LEGS · 13 个 ============
        inserted += ensure("BARBELL_BACK_SQUAT",     "杠铃后蹲",       "🏋️", "LEGS", "BARBELL",    "下肢力量之王 · 杠铃置于斜方肌", 710);
        inserted += ensure("BARBELL_FRONT_SQUAT",    "杠铃前蹲",       "🏋️", "LEGS", "BARBELL",    "杠铃前置 · 股四头 + 核心",       720);
        inserted += ensure("GOBLET_SQUAT",           "高脚杯深蹲",     "💪", "LEGS", "DUMBBELL",   "哑铃抱胸 · 新手友好",            730);
        inserted += ensure("BULGARIAN_SPLIT_SQUAT",  "保加利亚分腿蹲", "💪", "LEGS", "DUMBBELL",   "单腿训练 · 后脚抬高",            740);
        inserted += ensure("WALKING_LUNGE",          "行走箭步蹲",     "💪", "LEGS", "DUMBBELL",   "行进式弓步 · 臀腿动态训练",      750);
        inserted += ensure("LEG_PRESS",              "倒蹬机",         "⚙️", "LEGS", "MACHINE",    "腿部大重量训练 · 安全可控",      760);
        inserted += ensure("LEG_EXTENSION",          "腿屈伸",         "⚙️", "LEGS", "MACHINE",    "股四头肌孤立训练",                770);
        inserted += ensure("LEG_CURL",               "腿弯举",         "⚙️", "LEGS", "MACHINE",    "腘绳肌孤立训练 · 俯卧/坐姿",    780);
        inserted += ensure("HIP_THRUST",             "臀冲",           "🏋️", "LEGS", "BARBELL",    "臀大肌训练之王 · 杠铃压髋",      790);
        inserted += ensure("GLUTE_BRIDGE",           "臀桥",           "🤸", "LEGS", "BODYWEIGHT", "自重臀桥 · 臀大肌激活",          800);
        inserted += ensure("HACK_SQUAT",             "哈克深蹲",       "⚙️", "LEGS", "MACHINE",    "斜角倒蹬 · 股四头主导",          810);
        inserted += ensure("STIFF_LEG_DEADLIFT",     "直腿硬拉",       "🏋️", "LEGS", "BARBELL",    "腘绳肌 + 下背 · 膝几乎不弯",     820);
        inserted += ensure("SUMO_DEADLIFT",          "相扑硬拉",       "🏋️", "LEGS", "BARBELL",    "宽站距 · 内收肌 + 臀部参与多",   830);

        // ============ 小腿 CALVES · 3 个 ============
        inserted += ensure("STANDING_CALF_RAISE", "站姿提踵", "⚙️", "CALVES", "MACHINE",  "腓肠肌训练 · 直腿提踵",      910);
        inserted += ensure("SEATED_CALF_RAISE",   "坐姿提踵", "⚙️", "CALVES", "MACHINE",  "比目鱼肌训练 · 屈膝提踵",    920);
        inserted += ensure("DUMBBELL_CALF_RAISE", "哑铃提踵", "💪", "CALVES", "DUMBBELL", "无器械版 · 单边/双边均可",   930);

        // ============ 核心 CORE · 8 个 ============
        inserted += ensure("PLANK",              "平板支撑",       "🤸", "CORE", "BODYWEIGHT", "核心稳定性训练 · 经典等长",  1010);
        inserted += ensure("SIDE_PLANK",         "侧平板支撑",     "🤸", "CORE", "BODYWEIGHT", "腹斜肌训练 · 单侧支撑",      1020);
        inserted += ensure("CRUNCH",             "卷腹",           "🤸", "CORE", "BODYWEIGHT", "腹直肌上部训练",              1030);
        inserted += ensure("SIT_UP",             "仰卧起坐",       "🤸", "CORE", "BODYWEIGHT", "经典核心训练动作",            1040);
        inserted += ensure("HANGING_LEG_RAISE",  "悬垂举腿",       "🤸", "CORE", "BODYWEIGHT", "腹直肌下部 · 悬挂屈髋",      1050);
        inserted += ensure("CABLE_WOOD_CHOP",    "绳索砍柴",       "🔗", "CORE", "CABLE",      "腹斜肌旋转训练",              1060);
        inserted += ensure("RUSSIAN_TWIST",      "俄罗斯转体",     "🤸", "CORE", "BODYWEIGHT", "腹斜肌 · V 字平衡左右转",    1070);
        inserted += ensure("AB_WHEEL_ROLLOUT",   "腹肌轮",         "🤸", "CORE", "BODYWEIGHT", "核心整体训练 · 进阶动作",    1080);

        // ============ 前臂 FOREARM · 3 个 ============
        inserted += ensure("WRIST_CURL",         "腕弯举",         "💪", "FOREARM", "DUMBBELL", "前臂屈肌训练 · 掌心向上", 1110);
        inserted += ensure("REVERSE_WRIST_CURL", "反向腕弯举",     "💪", "FOREARM", "DUMBBELL", "前臂伸肌训练 · 掌心向下", 1120);
        inserted += ensure("FARMER_WALK",        "农夫走路",       "💪", "FOREARM", "DUMBBELL", "握力 + 全身稳定性训练",   1130);

        // ============ 有氧 CARDIO · 8 个 ============
        inserted += ensure("RUNNING",        "跑步",     "🏃", "CARDIO", "CARDIO", "户外跑步 · 长距离心肺训练",  1210);
        inserted += ensure("TREADMILL",      "跑步机",   "🏃", "CARDIO", "CARDIO", "室内跑步 · 可控配速",        1220);
        inserted += ensure("CYCLING",        "骑行",     "🚴", "CARDIO", "CARDIO", "户外/室内单车",              1230);
        inserted += ensure("ROWING",         "划船机",   "🚣", "CARDIO", "CARDIO", "全身性有氧 · 心肺 + 上肢",   1240);
        inserted += ensure("ELLIPTICAL",     "椭圆机",   "🏃", "CARDIO", "CARDIO", "低冲击有氧 · 关节友好",      1250);
        inserted += ensure("STAIR_CLIMBER",  "爬楼机",   "🪜", "CARDIO", "CARDIO", "下肢有氧 · 臀腿燃脂",        1260);
        inserted += ensure("JUMP_ROPE",      "跳绳",     "🪢", "CARDIO", "CARDIO", "高效燃脂 · 协调性训练",      1270);
        inserted += ensure("SWIMMING",       "游泳",     "🏊", "CARDIO", "CARDIO", "全身有氧 · 关节零冲击",      1280);

        if (inserted > 0) {
            log.info("[seeder] 训练动作库初始化完成，新增 {} 项（共 86 个内置动作）", inserted);
        }
    }

    /**
     * 幂等创建一条内置训练动作。
     * <p>已存在（按 exerciseKey + userId IS NULL 检索）则跳过，不覆盖运营在 admin 端的修改。
     */
    private int ensure(String exerciseKey, String displayName, String emoji,
                       String muscleGroup, String equipment, String description, int sortOrder) {
        if (trainingExerciseRepository.findByExerciseKeyAndUserIdIsNull(exerciseKey).isPresent()) {
            return 0;
        }
        TrainingExercise t = new TrainingExercise();
        t.setExerciseKey(exerciseKey);
        t.setDisplayName(displayName);
        t.setEmoji(emoji);
        t.setMuscleGroup(muscleGroup);
        t.setEquipment(equipment);
        t.setDescription(description);
        t.setSortOrder(sortOrder);
        t.setEnabled(true);
        t.setIsCustom(false);
        t.setUserId(null);
        trainingExerciseRepository.save(t);
        log.debug("[seeder] 创建训练动作：{} ({}/{}) {}", displayName, muscleGroup, equipment, exerciseKey);
        return 1;
    }
}
