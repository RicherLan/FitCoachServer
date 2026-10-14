-- Only adopt a complete schema. Partial/manual incompatible installations stop for inspection.
SET @fc_tables = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('challenge_session','challenge_audit'));
SET @fc_session = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='challenge_session' AND COLUMN_NAME IN ('id','user_id','project','rule_version','mode','level_no','difficulty','public_score','started_ms','week_start','training_date','state','reps','completed','elapsed_ms','payload'));
SET @fc_audit = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='challenge_audit' AND COLUMN_NAME IN ('id','session_id','actor','reason','previous_state','next_state','created_at'));
SET @fc_index = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='challenge_session' AND INDEX_NAME='idx_challenge_board');
SELECT CASE WHEN @fc_tables=0 THEN 'PENDING'
 WHEN @fc_tables=2 AND @fc_session=16 AND @fc_audit=7 AND @fc_index='project,rule_version,state,public_score,mode,week_start,reps' THEN 'APPLIED'
 ELSE 'CONFLICT' END;
