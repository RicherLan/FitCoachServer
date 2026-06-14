-- =============================================================================
-- 训练动作 emoji 一次性修正 SQL（FitCoach · 86 个内置动作 · v2 策略）
-- =============================================================================
-- 用途：把已部署 DB 中所有内置动作（user_id IS NULL）的 emoji 字段统一到 v2 策略。
--      策略：命中即用动作 emoji，未命中（卧推/推举/弯举/屈伸/划船/夹胸/提踵/卷腹…）一律 💪 兜底。
--
-- 安全：只更新 user_id IS NULL 的内置动作，不影响用户自定义动作（is_custom=true）。
--
-- 注意：如果运营已经在 admin 端手工改过某些 emoji 想保留，
--      请把对应 exercise_key 从下面的 SQL 中删掉再执行。
--
-- 执行：mysql -u <用户名> -p <数据库名> < fix_training_exercise_emoji_v2.sql
-- =============================================================================

BEGIN;

-- ---------- 默认兜底：所有内置动作先全部统一为 💪 ----------
UPDATE training_exercise SET emoji = '💪' WHERE user_id IS NULL;

-- ---------- 真实命中：精准命中的 19 个动作覆盖回去 ----------

-- 攀爬姿态 🧗：引体向上系列
UPDATE training_exercise SET emoji = '🧗'
 WHERE user_id IS NULL AND exercise_key IN ('PULL_UP','CHIN_UP');

-- 举重姿态 🏋️：深蹲 / 硬拉 / 臀冲（emoji 字面就是压杠下蹲的人）
UPDATE training_exercise SET emoji = '🏋️'
 WHERE user_id IS NULL AND exercise_key IN (
   'BARBELL_BACK_SQUAT','BARBELL_FRONT_SQUAT','GOBLET_SQUAT','HACK_SQUAT',
   'DEADLIFT','ROMANIAN_DEADLIFT','STIFF_LEG_DEADLIFT','SUMO_DEADLIFT',
   'HIP_THRUST'
 );

-- 跑步姿态 🏃：跑步 / 跑步机 / 椭圆机
UPDATE training_exercise SET emoji = '🏃'
 WHERE user_id IS NULL AND exercise_key IN ('RUNNING','TREADMILL','ELLIPTICAL');

-- 单一动作命中
UPDATE training_exercise SET emoji = '🚴' WHERE user_id IS NULL AND exercise_key = 'CYCLING';
UPDATE training_exercise SET emoji = '🚣' WHERE user_id IS NULL AND exercise_key = 'ROWING';
UPDATE training_exercise SET emoji = '🏊' WHERE user_id IS NULL AND exercise_key = 'SWIMMING';
UPDATE training_exercise SET emoji = '🪜' WHERE user_id IS NULL AND exercise_key = 'STAIR_CLIMBER';
UPDATE training_exercise SET emoji = '🪢' WHERE user_id IS NULL AND exercise_key = 'JUMP_ROPE';
UPDATE training_exercise SET emoji = '🚶' WHERE user_id IS NULL AND exercise_key = 'FARMER_WALK';

COMMIT;

-- ---------- 验证（执行完可选跑一下看分布） ----------
-- 预期结果：💪 67 行 · 🏋️ 9 行 · 🏃 3 行 · 🧗 2 行 · 🚴/🚣/🏊/🪜/🪢/🚶 各 1 行 = 共 86 行
SELECT emoji, COUNT(*) AS cnt
  FROM training_exercise
 WHERE user_id IS NULL
 GROUP BY emoji
 ORDER BY cnt DESC;
