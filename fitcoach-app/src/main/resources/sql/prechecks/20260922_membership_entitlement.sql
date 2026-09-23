-- 返回唯一状态：PENDING / APPLIED / CONFLICT。不修改业务数据。
SET @fc_table = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement');
SET @fc_source = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_membership' AND COLUMN_NAME IN ('user_id','plan_id','plan_code','activated_at','expires_at'));
SET @fc_columns = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement' AND (
  (COLUMN_NAME='id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='order_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64) OR
  (COLUMN_NAME='user_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='plan_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='YES') OR
  (COLUMN_NAME='plan_code' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=32) OR
  (COLUMN_NAME='duration_days' AND DATA_TYPE='int' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='granted_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES') OR
  (COLUMN_NAME='revoked' AND DATA_TYPE='bit' AND IS_NULLABLE='NO') OR
  (COLUMN_NAME='baseline_expires_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES')));
SET @fc_unique = (SELECT COUNT(*) FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement' AND NON_UNIQUE=0 GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='order_id') s);
SET @fc_user_index = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement' AND SEQ_IN_INDEX=1 AND COLUMN_NAME='user_id');
SET @fc_identity = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement' AND COLUMN_NAME='id' AND COLUMN_KEY='PRI' AND EXTRA LIKE '%auto_increment%');
SET @fc_sql = CASE
 WHEN @fc_source<>5 THEN 'SELECT ''CONFLICT'''
 WHEN @fc_table=0 THEN 'SELECT ''PENDING'''
 WHEN @fc_columns<>11 OR @fc_unique=0 OR @fc_user_index=0 OR @fc_identity<>1 THEN 'SELECT ''CONFLICT'''
 ELSE 'SELECT IF(EXISTS(SELECT 1 FROM user_membership m WHERE NOT EXISTS(SELECT 1 FROM membership_entitlement e WHERE e.user_id=m.user_id)), ''PENDING'', ''APPLIED'')'
 END;
PREPARE fc_statement FROM @fc_sql;
EXECUTE fc_statement;
DEALLOCATE PREPARE fc_statement;
