-- 部署前执行一次。旧客户端不传 AI 摘要，字段保持可空。
ALTER TABLE training_record
  ADD COLUMN ai_summary_json MEDIUMTEXT NULL,
  ADD COLUMN ai_exercise_key VARCHAR(64) NULL,
  ADD INDEX idx_training_record_ai_history (user_id, ai_exercise_key, date);
