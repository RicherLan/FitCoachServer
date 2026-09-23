-- 缺少全部新增项才执行；全部匹配则登记已应用；部分存在或定义不符则停止。
SET @fc_base = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='training_record' AND COLUMN_NAME IN ('user_id','date'));
SET @fc_present = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='training_record' AND COLUMN_NAME IN ('ai_summary_json','ai_exercise_key'));
SET @fc_valid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='training_record' AND IS_NULLABLE='YES' AND ((COLUMN_NAME='ai_summary_json' AND DATA_TYPE='mediumtext') OR (COLUMN_NAME='ai_exercise_key' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64)));
SET @fc_index = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='training_record' AND INDEX_NAME='idx_training_record_ai_history');
SET @fc_index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='training_record' AND INDEX_NAME='idx_training_record_ai_history');
SELECT CASE
 WHEN @fc_base<>2 THEN 'CONFLICT'
 WHEN @fc_present=0 AND @fc_index IS NULL THEN 'PENDING'
 WHEN @fc_valid=2 AND @fc_index='user_id,ai_exercise_key,date' AND @fc_index_non_unique=1 THEN 'APPLIED'
 ELSE 'CONFLICT' END;
