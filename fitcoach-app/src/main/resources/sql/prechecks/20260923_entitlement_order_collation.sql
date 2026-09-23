-- 新库可能尚未执行前面的权益建表迁移；预检允许PENDING，执行时按清单顺序处理。
SET @fc_order_collation = (
  SELECT COLLATION_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='payment_order' AND COLUMN_NAME='order_id'
    AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4'
);
SET @fc_entitlement_exists = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement');
SET @fc_entitlement_collation = (
  SELECT COLLATION_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='membership_entitlement' AND COLUMN_NAME='order_id'
    AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4'
    AND IS_NULLABLE='NO'
);
SELECT CASE
  WHEN @fc_order_collation IS NULL THEN 'CONFLICT'
  WHEN @fc_entitlement_exists=0 THEN 'PENDING'
  WHEN @fc_entitlement_collation IS NULL THEN 'CONFLICT'
  WHEN @fc_entitlement_collation=@fc_order_collation THEN 'APPLIED'
  ELSE 'PENDING' END;
