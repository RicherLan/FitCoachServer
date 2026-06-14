package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.common.i18n.I18nText;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健身动作 i18n 翻译表（Seeder 内部使用）。
 *
 * <p>把 26 个动作的 8 语言翻译集中在这里，避免 {@link ExerciseSeeder} 文件因翻译爆炸而难以阅读。
 *
 * <p><b>支持语言</b>（与 {@code FitCoachRN/src/i18n/locales/*} 对齐）：
 * zh-CN, en, ja, ko, fr, es, ru, ar — 8 种。
 *
 * <p><b>查找规则</b>：
 * <ul>
 *   <li>{@link #displayName(String)} 等方法接收 {@code exerciseKey}（如 "SQUAT"），返回该动作的多语言 JSON；</li>
 *   <li>没翻译的字段返回 {@code null}（让数据库列存 NULL），DTO 兜底用 entity 上的中文字段；</li>
 *   <li>翻译 map 用 {@link LinkedHashMap} 保证 JSON 输出顺序稳定，便于 DB review/diff。</li>
 * </ul>
 *
 * <p><b>编辑指南</b>：新增动作时只需在对应方法里加一行 {@code put("XXX", map(...))} 即可。
 * 翻译质量定位是"能让海外用户看懂"，专业术语优先用维基百科/Bodybuilding.com 通用译法。
 */
final class ExerciseI18n {

    private ExerciseI18n() {}

    private static final Map<String, Map<String, String>> DISPLAY_NAME = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> DESCRIPTION = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> MUSCLES = new LinkedHashMap<>();
    /** 肌群标题翻译，key 是 groupKey（CHEST/BACK/LEGS/...） */
    private static final Map<String, Map<String, String>> MUSCLE_GROUP_NAME = new LinkedHashMap<>();

    static {
        // ==================== 旧 8 个动作 ====================
        putExercise("SQUAT",
                map("zh-CN", "深蹲", "en", "Squat", "ja", "スクワット", "ko", "스쿼트",
                        "fr", "Squat", "es", "Sentadilla", "ru", "Приседания", "ar", "القرفصاء"),
                map("zh-CN", "锻炼下肢力量的基础复合动作", "en", "Foundational lower-body compound exercise",
                        "ja", "下半身の基本となるコンパウンド種目", "ko", "하체 근력의 기본 복합 운동",
                        "fr", "Exercice composé fondamental pour le bas du corps",
                        "es", "Ejercicio compuesto fundamental para el tren inferior",
                        "ru", "Базовое многосуставное упражнение для ног",
                        "ar", "تمرين مركب أساسي للجزء السفلي من الجسم"),
                map("zh-CN", "股四头肌 · 臀大肌 · 腘绳肌",
                        "en", "Quads · Glutes · Hamstrings", "ja", "大腿四頭筋・大臀筋・ハムストリング",
                        "ko", "대퇴사두근 · 둔근 · 햄스트링", "fr", "Quadriceps · Fessiers · Ischios",
                        "es", "Cuádriceps · Glúteos · Isquiotibiales",
                        "ru", "Квадрицепсы · Ягодицы · Бицепс бедра",
                        "ar", "العضلة الرباعية · الألوية · أوتار الركبة"));

        putExercise("BICEP_CURL",
                map("zh-CN", "哑铃弯举", "en", "Bicep Curl", "ja", "ダンベルカール", "ko", "덤벨 컬",
                        "fr", "Curl haltère", "es", "Curl con mancuerna", "ru", "Сгибание на бицепс",
                        "ar", "تمرين العضلة ذات الرأسين بالدمبل"),
                map("zh-CN", "针对肱二头肌的经典孤立动作", "en", "Classic isolation for the biceps",
                        "ja", "上腕二頭筋を狙う定番のアイソレーション",
                        "ko", "이두근을 위한 클래식 고립 운동",
                        "fr", "Exercice d'isolation classique pour les biceps",
                        "es", "Aislamiento clásico para el bíceps",
                        "ru", "Классическое изолированное упражнение на бицепс",
                        "ar", "تمرين عزل كلاسيكي للعضلة ذات الرأسين"),
                map("zh-CN", "肱二头肌 · 前臂", "en", "Biceps · Forearms",
                        "ja", "上腕二頭筋・前腕", "ko", "이두근 · 전완근",
                        "fr", "Biceps · Avant-bras", "es", "Bíceps · Antebrazos",
                        "ru", "Бицепс · Предплечья", "ar", "العضلة ذات الرأسين · الساعد"));

        putExercise("PUSH_UP",
                map("zh-CN", "俯卧撑", "en", "Push-Up", "ja", "腕立て伏せ", "ko", "푸시업",
                        "fr", "Pompe", "es", "Flexión", "ru", "Отжимание", "ar", "تمرين الضغط"),
                map("zh-CN", "经典上肢推类自重训练动作",
                        "en", "Classic bodyweight upper-body pushing exercise",
                        "ja", "上半身プッシュ系の定番自重トレーニング",
                        "ko", "상체 푸시의 클래식 맨몸 운동",
                        "fr", "Exercice de poussée au poids du corps emblématique",
                        "es", "Empuje clásico de tren superior con peso corporal",
                        "ru", "Классическое жимовое упражнение с собственным весом",
                        "ar", "تمرين دفع كلاسيكي للجزء العلوي بوزن الجسم"),
                map("zh-CN", "胸大肌 · 三角肌前束 · 肱三头肌",
                        "en", "Chest · Front Delts · Triceps",
                        "ja", "大胸筋・三角筋前部・上腕三頭筋",
                        "ko", "대흉근 · 전면 삼각근 · 삼두근",
                        "fr", "Pectoraux · Deltoïdes antérieurs · Triceps",
                        "es", "Pectorales · Deltoides anteriores · Tríceps",
                        "ru", "Грудные · Передние дельты · Трицепс",
                        "ar", "الصدر · الدالية الأمامية · العضلة الثلاثية"));

        putExercise("WIDE_PUSH_UP",
                map("zh-CN", "宽距俯卧撑", "en", "Wide Push-Up", "ja", "ワイド腕立て伏せ",
                        "ko", "와이드 푸시업", "fr", "Pompe large", "es", "Flexión amplia",
                        "ru", "Широкие отжимания", "ar", "تمرين الضغط بقبضة عريضة"),
                map("zh-CN", "更强调胸大肌外侧的俯卧撑变式",
                        "en", "Push-up variation that emphasises the outer chest",
                        "ja", "大胸筋の外側をより強調するプッシュアップ",
                        "ko", "대흉근 바깥쪽을 강조하는 푸시업 변형",
                        "fr", "Variante mettant l'accent sur les pectoraux externes",
                        "es", "Variante que enfatiza la parte externa del pecho",
                        "ru", "Вариация отжиманий с акцентом на внешнюю часть груди",
                        "ar", "تنويعة للضغط تركز على الجزء الخارجي من الصدر"),
                map("zh-CN", "胸大肌外侧 · 三角肌前束 · 肱三头肌",
                        "en", "Outer Chest · Front Delts · Triceps",
                        "ja", "大胸筋外側・三角筋前部・上腕三頭筋",
                        "ko", "대흉근 외측 · 전면 삼각근 · 삼두근",
                        "fr", "Pectoraux externes · Deltoïdes antérieurs · Triceps",
                        "es", "Pecho externo · Deltoides anteriores · Tríceps",
                        "ru", "Внешняя часть груди · Передние дельты · Трицепс",
                        "ar", "الصدر الخارجي · الدالية الأمامية · العضلة الثلاثية"));

        putExercise("LUNGE",
                map("zh-CN", "弓步蹲", "en", "Lunge", "ja", "ランジ", "ko", "런지",
                        "fr", "Fente", "es", "Zancada", "ru", "Выпад", "ar", "تمرين الهجوم"),
                map("zh-CN", "锻炼单侧腿部力量与平衡性的复合动作",
                        "en", "Compound exercise for unilateral leg strength and balance",
                        "ja", "片脚の筋力とバランスを鍛えるコンパウンド種目",
                        "ko", "한쪽 다리의 근력과 균형을 단련하는 복합 운동",
                        "fr", "Exercice composé pour la force unilatérale et l'équilibre",
                        "es", "Ejercicio compuesto para fuerza unilateral y equilibrio",
                        "ru", "Многосуставное упражнение для односторонней силы и баланса",
                        "ar", "تمرين مركب لقوة الساق الواحدة والتوازن"),
                map("zh-CN", "股四头肌 · 臀大肌 · 腘绳肌",
                        "en", "Quads · Glutes · Hamstrings", "ja", "大腿四頭筋・大臀筋・ハムストリング",
                        "ko", "대퇴사두근 · 둔근 · 햄스트링", "fr", "Quadriceps · Fessiers · Ischios",
                        "es", "Cuádriceps · Glúteos · Isquiotibiales",
                        "ru", "Квадрицепсы · Ягодицы · Бицепс бедра",
                        "ar", "العضلة الرباعية · الألوية · أوتار الركبة"));

        putExercise("LATERAL_RAISE",
                map("zh-CN", "侧平举", "en", "Lateral Raise", "ja", "サイドレイズ", "ko", "사이드 레터럴 레이즈",
                        "fr", "Élévation latérale", "es", "Elevación lateral",
                        "ru", "Махи в стороны", "ar", "الرفع الجانبي"),
                map("zh-CN", "针对三角肌中束的经典孤立动作",
                        "en", "Classic isolation for the lateral delts",
                        "ja", "三角筋中部を狙う定番アイソレーション",
                        "ko", "삼각근 측면을 위한 클래식 고립 운동",
                        "fr", "Isolation classique pour les deltoïdes latéraux",
                        "es", "Aislamiento clásico para el deltoides medio",
                        "ru", "Классическое изолированное упражнение на средние дельты",
                        "ar", "تمرين عزل كلاسيكي للدالية الجانبية"),
                map("zh-CN", "三角肌中束", "en", "Lateral Delts", "ja", "三角筋中部",
                        "ko", "측면 삼각근", "fr", "Deltoïdes latéraux", "es", "Deltoides medio",
                        "ru", "Средние дельты", "ar", "الدالية الجانبية"));

        putExercise("BENT_OVER_ROW",
                map("zh-CN", "俯身划船", "en", "Bent-Over Row", "ja", "ベントオーバーロウ",
                        "ko", "벤트오버 로우", "fr", "Rowing buste penché", "es", "Remo inclinado",
                        "ru", "Тяга в наклоне", "ar", "تجديف انحناء"),
                map("zh-CN", "针对背阔肌的经典复合拉类动作",
                        "en", "Classic compound pull for the lats",
                        "ja", "広背筋を狙う定番のコンパウンドプル",
                        "ko", "광배근을 위한 클래식 복합 당기기 운동",
                        "fr", "Tirage composé classique pour les dorsaux",
                        "es", "Tirón compuesto clásico para los dorsales",
                        "ru", "Классическая многосуставная тяга для широчайших",
                        "ar", "تمرين سحب مركب كلاسيكي للظهر"),
                map("zh-CN", "背阔肌 · 菱形肌 · 肱二头肌",
                        "en", "Lats · Rhomboids · Biceps",
                        "ja", "広背筋・菱形筋・上腕二頭筋",
                        "ko", "광배근 · 능형근 · 이두근",
                        "fr", "Dorsaux · Rhomboïdes · Biceps",
                        "es", "Dorsales · Romboides · Bíceps",
                        "ru", "Широчайшие · Ромбовидные · Бицепс",
                        "ar", "الظهر · المعينة · العضلة ذات الرأسين"));

        putExercise("OVERHEAD_TRICEP_EXTENSION",
                map("zh-CN", "过头臂屈伸", "en", "Overhead Tricep Extension",
                        "ja", "オーバーヘッドトライセプスエクステンション",
                        "ko", "오버헤드 트라이셉 익스텐션",
                        "fr", "Extension triceps au-dessus de la tête",
                        "es", "Extensión de tríceps sobre la cabeza",
                        "ru", "Французский жим из-за головы",
                        "ar", "مدّ العضلة الثلاثية فوق الرأس"),
                map("zh-CN", "针对肱三头肌的孤立训练动作",
                        "en", "Isolation drill targeting the triceps",
                        "ja", "上腕三頭筋を狙うアイソレーション種目",
                        "ko", "삼두근을 위한 고립 운동",
                        "fr", "Exercice d'isolation pour les triceps",
                        "es", "Ejercicio de aislamiento para el tríceps",
                        "ru", "Изолированное упражнение на трицепс",
                        "ar", "تمرين عزل يستهدف العضلة الثلاثية"),
                map("zh-CN", "肱三头肌", "en", "Triceps", "ja", "上腕三頭筋", "ko", "삼두근",
                        "fr", "Triceps", "es", "Tríceps", "ru", "Трицепс", "ar", "العضلة الثلاثية"));

        // ==================== 新 18 个动作 ====================
        // LEGS
        putExercise("BARBELL_BACK_SQUAT",
                map("zh-CN", "杠铃后蹲", "en", "Barbell Back Squat", "ja", "バーベルバックスクワット",
                        "ko", "바벨 백스쿼트", "fr", "Squat barre nuque", "es", "Sentadilla con barra",
                        "ru", "Приседания со штангой", "ar", "القرفصاء بالبار"),
                map("zh-CN", "下肢力量训练的王牌复合动作",
                        "en", "King compound exercise for lower-body strength",
                        "ja", "下半身筋力の王道コンパウンド種目",
                        "ko", "하체 근력 훈련의 왕도 복합 운동",
                        "fr", "Exercice composé roi pour la force du bas du corps",
                        "es", "Ejercicio compuesto rey para la fuerza del tren inferior",
                        "ru", "Король многосуставных упражнений для нижней части тела",
                        "ar", "ملك التمارين المركبة لقوة الجزء السفلي"),
                map("zh-CN", "股四头肌 · 臀大肌 · 腘绳肌 · 核心",
                        "en", "Quads · Glutes · Hamstrings · Core",
                        "ja", "大腿四頭筋・大臀筋・ハムストリング・体幹",
                        "ko", "대퇴사두근 · 둔근 · 햄스트링 · 코어",
                        "fr", "Quadriceps · Fessiers · Ischios · Sangle abdominale",
                        "es", "Cuádriceps · Glúteos · Isquiotibiales · Core",
                        "ru", "Квадрицепсы · Ягодицы · Бицепс бедра · Кор",
                        "ar", "العضلة الرباعية · الألوية · أوتار الركبة · الجذع"));

        putExercise("LEG_PRESS",
                map("zh-CN", "腿举", "en", "Leg Press", "ja", "レッグプレス", "ko", "레그 프레스",
                        "fr", "Presse à cuisses", "es", "Prensa de piernas", "ru", "Жим ногами",
                        "ar", "ضغط الساقين"),
                map("zh-CN", "孤立训练下肢的器械动作",
                        "en", "Machine exercise that isolates the lower body",
                        "ja", "下半身を狙う高負荷マシン種目",
                        "ko", "하체를 고립시키는 머신 운동",
                        "fr", "Exercice machine ciblant le bas du corps",
                        "es", "Ejercicio en máquina para aislar el tren inferior",
                        "ru", "Тренажёр для изолированной работы ног",
                        "ar", "تمرين على آلة يعزل الجزء السفلي"),
                map("zh-CN", "股四头肌 · 臀大肌", "en", "Quads · Glutes",
                        "ja", "大腿四頭筋・大臀筋", "ko", "대퇴사두근 · 둔근",
                        "fr", "Quadriceps · Fessiers", "es", "Cuádriceps · Glúteos",
                        "ru", "Квадрицепсы · Ягодицы", "ar", "العضلة الرباعية · الألوية"));

        putExercise("HIP_THRUST",
                map("zh-CN", "臀桥（杠铃臀冲）", "en", "Hip Thrust", "ja", "ヒップスラスト",
                        "ko", "힙 쓰러스트", "fr", "Hip Thrust", "es", "Hip Thrust",
                        "ru", "Ягодичный мост со штангой", "ar", "دفع الورك"),
                map("zh-CN", "孤立训练臀大肌的黄金动作",
                        "en", "Gold-standard glute isolation exercise",
                        "ja", "大臀筋アイソレーションのゴールドスタンダード",
                        "ko", "둔근 고립의 황금 운동",
                        "fr", "Exercice d'isolation phare pour les fessiers",
                        "es", "Ejercicio dorado de aislamiento glúteo",
                        "ru", "Золотое упражнение для изоляции ягодиц",
                        "ar", "تمرين العزل الذهبي للألوية"),
                map("zh-CN", "臀大肌 · 腘绳肌", "en", "Glutes · Hamstrings",
                        "ja", "大臀筋・ハムストリング", "ko", "둔근 · 햄스트링",
                        "fr", "Fessiers · Ischios", "es", "Glúteos · Isquiotibiales",
                        "ru", "Ягодицы · Бицепс бедра", "ar", "الألوية · أوتار الركبة"));

        // ARMS
        putExercise("BARBELL_CURL",
                map("zh-CN", "杠铃弯举", "en", "Barbell Curl", "ja", "バーベルカール",
                        "ko", "바벨 컬", "fr", "Curl barre", "es", "Curl con barra",
                        "ru", "Подъём штанги на бицепс", "ar", "تمرين العضلة ذات الرأسين بالبار"),
                map("zh-CN", "肱二头肌力量训练首选",
                        "en", "Go-to bicep strength builder",
                        "ja", "上腕二頭筋の筋力アップ第一候補",
                        "ko", "이두근 근력 훈련의 최선택",
                        "fr", "Référence pour la force des biceps",
                        "es", "Opción principal para fuerza de bíceps",
                        "ru", "Главное упражнение для силы бицепса",
                        "ar", "الخيار الأول لقوة العضلة ذات الرأسين"),
                map("zh-CN", "肱二头肌 · 前臂", "en", "Biceps · Forearms",
                        "ja", "上腕二頭筋・前腕", "ko", "이두근 · 전완근",
                        "fr", "Biceps · Avant-bras", "es", "Bíceps · Antebrazos",
                        "ru", "Бицепс · Предплечья", "ar", "العضلة ذات الرأسين · الساعد"));

        putExercise("CABLE_PUSHDOWN",
                map("zh-CN", "绳索下压", "en", "Cable Pushdown", "ja", "ケーブルプッシュダウン",
                        "ko", "케이블 푸시다운", "fr", "Extension triceps poulie",
                        "es", "Extensión de tríceps en polea", "ru", "Разгибание на блоке",
                        "ar", "ضغط الكابل لأسفل"),
                map("zh-CN", "针对肱三头肌的经典孤立动作",
                        "en", "Classic tricep isolation exercise",
                        "ja", "上腕三頭筋アイソレーションの定番",
                        "ko", "삼두근을 위한 클래식 고립 운동",
                        "fr", "Exercice d'isolation classique pour les triceps",
                        "es", "Aislamiento clásico para el tríceps",
                        "ru", "Классическое изолированное упражнение на трицепс",
                        "ar", "تمرين عزل كلاسيكي للعضلة الثلاثية"),
                map("zh-CN", "肱三头肌", "en", "Triceps", "ja", "上腕三頭筋", "ko", "삼두근",
                        "fr", "Triceps", "es", "Tríceps", "ru", "Трицепс", "ar", "العضلة الثلاثية"));

        putExercise("SKULL_CRUSHER",
                map("zh-CN", "仰卧臂屈伸（窄距）", "en", "Skull Crusher",
                        "ja", "ライイングトライセプスエクステンション",
                        "ko", "스컬 크러셔", "fr", "Skull Crusher",
                        "es", "Skull Crusher", "ru", "Французский жим лёжа",
                        "ar", "تمرين كاسر الجمجمة"),
                map("zh-CN", "孤立训练肱三头肌长头的动作",
                        "en", "Isolation drill targeting the long head of the triceps",
                        "ja", "上腕三頭筋長頭を狙うアイソレーション",
                        "ko", "삼두근 장두를 위한 고립 운동",
                        "fr", "Exercice d'isolation pour le chef long du triceps",
                        "es", "Aislamiento para la cabeza larga del tríceps",
                        "ru", "Изолирующее упражнение на длинную головку трицепса",
                        "ar", "تمرين عزل يستهدف الرأس الطويل للعضلة الثلاثية"),
                map("zh-CN", "肱三头肌（长头）", "en", "Triceps (Long Head)",
                        "ja", "上腕三頭筋（長頭）", "ko", "삼두근 (장두)",
                        "fr", "Triceps (chef long)", "es", "Tríceps (cabeza larga)",
                        "ru", "Трицепс (длинная головка)", "ar", "العضلة الثلاثية (الرأس الطويل)"));

        // CHEST
        putExercise("BARBELL_BENCH_PRESS",
                map("zh-CN", "杠铃卧推", "en", "Barbell Bench Press", "ja", "バーベルベンチプレス",
                        "ko", "바벨 벤치프레스", "fr", "Développé couché barre",
                        "es", "Press de banca con barra", "ru", "Жим лёжа со штангой",
                        "ar", "بنش بريس بالبار"),
                map("zh-CN", "上肢推类力量训练的王牌动作",
                        "en", "King exercise for upper-body pushing strength",
                        "ja", "上半身プッシュ系筋力の王道種目",
                        "ko", "상체 푸시 근력 훈련의 왕도",
                        "fr", "Exercice roi de la poussée du haut du corps",
                        "es", "Ejercicio rey del empuje de tren superior",
                        "ru", "Король упражнений на жим для верхней части тела",
                        "ar", "ملك تمارين الدفع للجزء العلوي"),
                map("zh-CN", "胸大肌 · 三角肌前束 · 肱三头肌",
                        "en", "Chest · Front Delts · Triceps",
                        "ja", "大胸筋・三角筋前部・上腕三頭筋",
                        "ko", "대흉근 · 전면 삼각근 · 삼두근",
                        "fr", "Pectoraux · Deltoïdes antérieurs · Triceps",
                        "es", "Pectorales · Deltoides anteriores · Tríceps",
                        "ru", "Грудные · Передние дельты · Трицепс",
                        "ar", "الصدر · الدالية الأمامية · العضلة الثلاثية"));

        putExercise("INCLINE_BARBELL_BENCH_PRESS",
                map("zh-CN", "上斜杠铃卧推", "en", "Incline Barbell Bench Press",
                        "ja", "インクラインバーベルベンチプレス",
                        "ko", "인클라인 바벨 벤치프레스",
                        "fr", "Développé incliné barre",
                        "es", "Press inclinado con barra",
                        "ru", "Жим штанги на наклонной скамье",
                        "ar", "بنش بريس مائل بالبار"),
                map("zh-CN", "更强调胸大肌上束的卧推变式",
                        "en", "Bench variation emphasising the upper chest",
                        "ja", "大胸筋上部をより強調するベンチ変種",
                        "ko", "대흉근 상부를 강조하는 벤치프레스 변형",
                        "fr", "Variante mettant l'accent sur le haut des pectoraux",
                        "es", "Variante que enfatiza la parte superior del pecho",
                        "ru", "Вариант жима с акцентом на верхнюю часть груди",
                        "ar", "تنويعة للبنش بريس تركز على الجزء العلوي من الصدر"),
                map("zh-CN", "胸大肌上束 · 三角肌前束",
                        "en", "Upper Chest · Front Delts",
                        "ja", "大胸筋上部・三角筋前部",
                        "ko", "대흉근 상부 · 전면 삼각근",
                        "fr", "Haut des pectoraux · Deltoïdes antérieurs",
                        "es", "Pecho superior · Deltoides anteriores",
                        "ru", "Верх груди · Передние дельты",
                        "ar", "الصدر العلوي · الدالية الأمامية"));

        putExercise("DUMBBELL_BENCH_PRESS",
                map("zh-CN", "哑铃卧推", "en", "Dumbbell Bench Press",
                        "ja", "ダンベルベンチプレス", "ko", "덤벨 벤치프레스",
                        "fr", "Développé couché haltères",
                        "es", "Press de banca con mancuernas",
                        "ru", "Жим гантелей лёжа",
                        "ar", "بنش بريس بالدمبل"),
                map("zh-CN", "卧推中的经典哑铃变式，胸肌发力更聚焦",
                        "en", "Dumbbell bench variation with more focused chest activation",
                        "ja", "ベンチプレスのダンベル変種、胸への意識が集中しやすい",
                        "ko", "벤치프레스의 덤벨 변형, 가슴 자극이 더 집중됨",
                        "fr", "Variante haltères du développé, sollicitation pectorale plus ciblée",
                        "es", "Variante con mancuernas, activación pectoral más focalizada",
                        "ru", "Вариация жима с гантелями для более прицельной работы груди",
                        "ar", "تنويعة دمبل للبنش بتركيز أكبر على الصدر"),
                map("zh-CN", "胸大肌 · 三角肌前束 · 肱三头肌",
                        "en", "Chest · Front Delts · Triceps",
                        "ja", "大胸筋・三角筋前部・上腕三頭筋",
                        "ko", "대흉근 · 전면 삼각근 · 삼두근",
                        "fr", "Pectoraux · Deltoïdes antérieurs · Triceps",
                        "es", "Pectorales · Deltoides anteriores · Tríceps",
                        "ru", "Грудные · Передние дельты · Трицепс",
                        "ar", "الصدر · الدالية الأمامية · العضلة الثلاثية"));

        putExercise("INCLINE_DUMBBELL_BENCH_PRESS",
                map("zh-CN", "上斜哑铃卧推", "en", "Incline Dumbbell Bench Press",
                        "ja", "インクラインダンベルベンチプレス",
                        "ko", "인클라인 덤벨 벤치프레스",
                        "fr", "Développé incliné haltères",
                        "es", "Press inclinado con mancuernas",
                        "ru", "Жим гантелей на наклонной скамье",
                        "ar", "بنش بريس مائل بالدمبل"),
                map("zh-CN", "上胸塑形动作，比杠铃版稳定性需求更高",
                        "en", "Upper-chest shaper, demands more stabilisation than the barbell version",
                        "ja", "上部胸の形作り、バーベル版より安定性が問われる",
                        "ko", "상부 가슴 형성, 바벨 버전보다 더 많은 안정성을 요구함",
                        "fr", "Travail du haut des pectoraux, plus exigeant en stabilisation que la barre",
                        "es", "Modela el pecho superior, exige más estabilidad que la barra",
                        "ru", "Формирует верх груди, требует больше стабилизации, чем со штангой",
                        "ar", "ينحت الصدر العلوي ويتطلب ثباتاً أكبر من نسخة البار"),
                map("zh-CN", "胸大肌上束", "en", "Upper Chest",
                        "ja", "大胸筋上部", "ko", "대흉근 상부",
                        "fr", "Haut des pectoraux", "es", "Pecho superior",
                        "ru", "Верх груди", "ar", "الصدر العلوي"));

        putExercise("CHEST_FLY_MACHINE",
                map("zh-CN", "蝴蝶机夹胸", "en", "Chest Fly Machine",
                        "ja", "チェストフライマシン", "ko", "체스트 플라이 머신",
                        "fr", "Pec Deck", "es", "Máquina de aperturas",
                        "ru", "Сведение в тренажёре", "ar", "آلة فتح وضم الصدر"),
                map("zh-CN", "孤立胸大肌中缝的器械动作",
                        "en", "Machine isolation targeting the inner chest",
                        "ja", "大胸筋の中央を狙うマシンアイソレーション",
                        "ko", "대흉근 안쪽을 위한 머신 고립 운동",
                        "fr", "Exercice machine ciblant l'intérieur des pectoraux",
                        "es", "Aislamiento en máquina para el pecho interno",
                        "ru", "Тренажёрная изоляция внутренней части груди",
                        "ar", "تمرين عزل على آلة يستهدف وسط الصدر"),
                map("zh-CN", "胸大肌（中缝）", "en", "Inner Chest",
                        "ja", "大胸筋（中央）", "ko", "대흉근 (안쪽)",
                        "fr", "Pectoraux (intérieur)", "es", "Pecho interno",
                        "ru", "Внутренняя часть груди", "ar", "وسط الصدر"));

        // SHOULDERS
        putExercise("OVERHEAD_PRESS",
                map("zh-CN", "站姿杠铃肩推", "en", "Overhead Press",
                        "ja", "オーバーヘッドプレス", "ko", "오버헤드 프레스",
                        "fr", "Développé militaire", "es", "Press militar",
                        "ru", "Жим штанги стоя", "ar", "الضغط فوق الرأس"),
                map("zh-CN", "上肢推类的核心复合动作",
                        "en", "Core compound exercise for upper-body pushing",
                        "ja", "上半身プッシュ系の中核コンパウンド",
                        "ko", "상체 푸시의 핵심 복합 운동",
                        "fr", "Exercice composé central de poussée du haut du corps",
                        "es", "Ejercicio compuesto central de empuje superior",
                        "ru", "Ключевое жимовое многосуставное упражнение",
                        "ar", "تمرين دفع مركب أساسي للجزء العلوي"),
                map("zh-CN", "三角肌前束 · 三角肌中束 · 肱三头肌",
                        "en", "Front Delts · Lateral Delts · Triceps",
                        "ja", "三角筋前部・三角筋中部・上腕三頭筋",
                        "ko", "전면 삼각근 · 측면 삼각근 · 삼두근",
                        "fr", "Deltoïdes antérieurs · Latéraux · Triceps",
                        "es", "Deltoides anteriores · Medios · Tríceps",
                        "ru", "Передние дельты · Средние дельты · Трицепс",
                        "ar", "الدالية الأمامية · الدالية الجانبية · العضلة الثلاثية"));

        putExercise("SEATED_DUMBBELL_PRESS",
                map("zh-CN", "坐姿哑铃肩推", "en", "Seated Dumbbell Press",
                        "ja", "シーテッドダンベルプレス", "ko", "시티드 덤벨 프레스",
                        "fr", "Développé haltères assis", "es", "Press con mancuernas sentado",
                        "ru", "Жим гантелей сидя", "ar", "ضغط الدمبل بالجلوس"),
                map("zh-CN", "孤立训练肩部前/中束的稳定版本",
                        "en", "Stable variation isolating the front and lateral delts",
                        "ja", "三角筋前部・中部を狙う安定版バリエーション",
                        "ko", "전면/측면 삼각근을 위한 안정적 변형",
                        "fr", "Variante stable isolant les deltoïdes antérieurs et latéraux",
                        "es", "Versión estable que aísla deltoides anterior y medio",
                        "ru", "Стабильная вариация для изоляции передних и средних дельт",
                        "ar", "نسخة ثابتة تعزل الدالية الأمامية والجانبية"),
                map("zh-CN", "三角肌前束 · 三角肌中束",
                        "en", "Front Delts · Lateral Delts",
                        "ja", "三角筋前部・三角筋中部",
                        "ko", "전면 삼각근 · 측면 삼각근",
                        "fr", "Deltoïdes antérieurs · Latéraux",
                        "es", "Deltoides anteriores · Medios",
                        "ru", "Передние дельты · Средние дельты",
                        "ar", "الدالية الأمامية · الدالية الجانبية"));

        putExercise("FRONT_RAISE",
                map("zh-CN", "前平举", "en", "Front Raise", "ja", "フロントレイズ",
                        "ko", "프론트 레이즈", "fr", "Élévation frontale",
                        "es", "Elevación frontal", "ru", "Подъём перед собой",
                        "ar", "الرفع الأمامي"),
                map("zh-CN", "针对三角肌前束的孤立动作",
                        "en", "Isolation exercise for the front delts",
                        "ja", "三角筋前部を狙うアイソレーション",
                        "ko", "전면 삼각근을 위한 고립 운동",
                        "fr", "Exercice d'isolation pour les deltoïdes antérieurs",
                        "es", "Ejercicio de aislamiento para el deltoides anterior",
                        "ru", "Изолированное упражнение на передние дельты",
                        "ar", "تمرين عزل للدالية الأمامية"),
                map("zh-CN", "三角肌前束", "en", "Front Delts", "ja", "三角筋前部",
                        "ko", "전면 삼각근", "fr", "Deltoïdes antérieurs",
                        "es", "Deltoides anterior", "ru", "Передние дельты",
                        "ar", "الدالية الأمامية"));

        // BACK
        putExercise("LAT_PULLDOWN",
                map("zh-CN", "高位下拉", "en", "Lat Pulldown", "ja", "ラットプルダウン",
                        "ko", "랫 풀다운", "fr", "Tirage vertical poulie haute",
                        "es", "Jalón al pecho", "ru", "Тяга верхнего блока",
                        "ar", "سحب البكرة العلوية"),
                map("zh-CN", "背阔肌训练入门首选器械动作",
                        "en", "Beginner-friendly machine exercise for the lats",
                        "ja", "広背筋トレ入門の定番マシン",
                        "ko", "광배근 훈련 입문에 적합한 머신 운동",
                        "fr", "Exercice machine d'introduction aux dorsaux",
                        "es", "Ejercicio en máquina ideal para iniciar dorsales",
                        "ru", "Тренажёр для начинающих, прорабатывает широчайшие",
                        "ar", "تمرين آلة مثالي للمبتدئين للظهر"),
                map("zh-CN", "背阔肌 · 大圆肌 · 肱二头肌",
                        "en", "Lats · Teres Major · Biceps",
                        "ja", "広背筋・大円筋・上腕二頭筋",
                        "ko", "광배근 · 대원근 · 이두근",
                        "fr", "Dorsaux · Grand rond · Biceps",
                        "es", "Dorsales · Redondo mayor · Bíceps",
                        "ru", "Широчайшие · Большая круглая · Бицепс",
                        "ar", "الظهر · العضلة المدورة الكبرى · العضلة ذات الرأسين"));

        putExercise("SEATED_CABLE_ROW",
                map("zh-CN", "坐姿绳索划船", "en", "Seated Cable Row",
                        "ja", "シーテッドケーブルロウ", "ko", "시티드 케이블 로우",
                        "fr", "Rowing assis poulie basse",
                        "es", "Remo sentado en polea",
                        "ru", "Тяга в блочном тренажёре сидя",
                        "ar", "تجديف الكابل بالجلوس"),
                map("zh-CN", "厚度训练的经典动作，对腰背更友好",
                        "en", "Classic back-thickness builder, easier on the lower back",
                        "ja", "背中の厚みを作る定番、腰背に優しい",
                        "ko", "등 두께를 위한 클래식 운동, 허리에 부담이 적음",
                        "fr", "Référence pour l'épaisseur du dos, ménage le bas du dos",
                        "es", "Clásico para grosor de espalda, más amable con la zona lumbar",
                        "ru", "Классика для толщины спины, бережёт поясницу",
                        "ar", "تمرين كلاسيكي لسماكة الظهر، ألطف على أسفل الظهر"),
                map("zh-CN", "背阔肌 · 菱形肌 · 斜方肌中下",
                        "en", "Lats · Rhomboids · Mid/Lower Traps",
                        "ja", "広背筋・菱形筋・僧帽筋中下部",
                        "ko", "광배근 · 능형근 · 승모근 중하부",
                        "fr", "Dorsaux · Rhomboïdes · Trapèzes moyens/inférieurs",
                        "es", "Dorsales · Romboides · Trapecio medio/inferior",
                        "ru", "Широчайшие · Ромбовидные · Средние/нижние трапеции",
                        "ar", "الظهر · المعينة · شبه المنحرف الأوسط/السفلي"));

        putExercise("BARBELL_ROW",
                map("zh-CN", "杠铃划船", "en", "Barbell Row", "ja", "ベントオーバーバーベルロウ",
                        "ko", "바벨 로우", "fr", "Rowing barre", "es", "Remo con barra",
                        "ru", "Тяга штанги в наклоне", "ar", "تجديف بالبار"),
                map("zh-CN", "经典背部厚度训练复合动作",
                        "en", "Classic compound exercise for back thickness",
                        "ja", "背中の厚みを作る定番コンパウンド",
                        "ko", "등 두께를 위한 클래식 복합 운동",
                        "fr", "Exercice composé classique pour l'épaisseur du dos",
                        "es", "Compuesto clásico para grosor de espalda",
                        "ru", "Классическое многосуставное упражнение для толщины спины",
                        "ar", "تمرين مركب كلاسيكي لسماكة الظهر"),
                map("zh-CN", "背阔肌 · 菱形肌 · 后三角肌",
                        "en", "Lats · Rhomboids · Rear Delts",
                        "ja", "広背筋・菱形筋・三角筋後部",
                        "ko", "광배근 · 능형근 · 후면 삼각근",
                        "fr", "Dorsaux · Rhomboïdes · Deltoïdes postérieurs",
                        "es", "Dorsales · Romboides · Deltoides posterior",
                        "ru", "Широчайшие · Ромбовидные · Задние дельты",
                        "ar", "الظهر · المعينة · الدالية الخلفية"));

        putExercise("PULL_UP",
                map("zh-CN", "引体向上", "en", "Pull-Up", "ja", "懸垂", "ko", "풀업",
                        "fr", "Traction", "es", "Dominada", "ru", "Подтягивание",
                        "ar", "العقلة"),
                map("zh-CN", "上肢自重训练王者动作",
                        "en", "King of bodyweight upper-body exercises",
                        "ja", "上半身自重トレの王様",
                        "ko", "상체 맨몸 운동의 왕도",
                        "fr", "Roi des exercices au poids du corps pour le haut du corps",
                        "es", "Rey de los ejercicios con peso corporal de tren superior",
                        "ru", "Король упражнений с собственным весом для верха тела",
                        "ar", "ملك تمارين الجزء العلوي بوزن الجسم"),
                map("zh-CN", "背阔肌 · 大圆肌 · 肱二头肌",
                        "en", "Lats · Teres Major · Biceps",
                        "ja", "広背筋・大円筋・上腕二頭筋",
                        "ko", "광배근 · 대원근 · 이두근",
                        "fr", "Dorsaux · Grand rond · Biceps",
                        "es", "Dorsales · Redondo mayor · Bíceps",
                        "ru", "Широчайшие · Большая круглая · Бицепс",
                        "ar", "الظهر · العضلة المدورة الكبرى · العضلة ذات الرأسين"));

        // ==================== 肌群 ====================
        MUSCLE_GROUP_NAME.put("CHEST", map("zh-CN", "胸", "en", "Chest", "ja", "胸", "ko", "가슴",
                "fr", "Pectoraux", "es", "Pecho", "ru", "Грудь", "ar", "الصدر"));
        MUSCLE_GROUP_NAME.put("BACK", map("zh-CN", "背", "en", "Back", "ja", "背中", "ko", "등",
                "fr", "Dos", "es", "Espalda", "ru", "Спина", "ar", "الظهر"));
        MUSCLE_GROUP_NAME.put("LEGS", map("zh-CN", "腿", "en", "Legs", "ja", "脚", "ko", "다리",
                "fr", "Jambes", "es", "Piernas", "ru", "Ноги", "ar", "الساقين"));
        MUSCLE_GROUP_NAME.put("SHOULDERS", map("zh-CN", "肩", "en", "Shoulders", "ja", "肩", "ko", "어깨",
                "fr", "Épaules", "es", "Hombros", "ru", "Плечи", "ar", "الأكتاف"));
        MUSCLE_GROUP_NAME.put("ARMS", map("zh-CN", "手臂", "en", "Arms", "ja", "腕", "ko", "팔",
                "fr", "Bras", "es", "Brazos", "ru", "Руки", "ar", "الذراعين"));
        MUSCLE_GROUP_NAME.put("CORE", map("zh-CN", "核心", "en", "Core", "ja", "体幹", "ko", "코어",
                "fr", "Sangle abdominale", "es", "Core", "ru", "Кор", "ar", "الجذع"));
        MUSCLE_GROUP_NAME.put("FULL_BODY", map("zh-CN", "全身", "en", "Full Body", "ja", "全身",
                "ko", "전신", "fr", "Corps entier", "es", "Cuerpo completo",
                "ru", "Всё тело", "ar", "الجسم كاملاً"));
    }

    // ====== 对外 API ======

    /** 取动作 displayName 的多语言 JSON，找不到返回 null */
    static String displayName(String exerciseKey) {
        return I18nText.toJson(DISPLAY_NAME.get(exerciseKey));
    }

    /** 取动作 description 的多语言 JSON，找不到返回 null */
    static String description(String exerciseKey) {
        return I18nText.toJson(DESCRIPTION.get(exerciseKey));
    }

    /** 取动作 muscles 的多语言 JSON，找不到返回 null */
    static String muscles(String exerciseKey) {
        return I18nText.toJson(MUSCLES.get(exerciseKey));
    }

    /** 取肌群 displayName 的多语言 JSON */
    static String muscleGroupName(String groupKey) {
        return I18nText.toJson(MUSCLE_GROUP_NAME.get(groupKey));
    }

    // ====== 内部 helper ======

    private static void putExercise(String key,
                                    Map<String, String> displayName,
                                    Map<String, String> description,
                                    Map<String, String> muscles) {
        DISPLAY_NAME.put(key, displayName);
        DESCRIPTION.put(key, description);
        MUSCLES.put(key, muscles);
    }

    /**
     * Java 9+ Map.of 上限 10 个 entry，而且不接受 null。这里手写一个能接受可变参数的有序版本。
     * <p>参数必须成对：key1, value1, key2, value2, ...
     */
    private static LinkedHashMap<String, String> map(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("map() expects even number of args, got " + pairs.length);
        }
        LinkedHashMap<String, String> m = new LinkedHashMap<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }
}
