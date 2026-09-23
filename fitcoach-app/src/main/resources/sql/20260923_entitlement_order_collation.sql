-- 对齐订单关联字段的排序规则；不修改已发布的旧迁移，不删除订单/权益数据。
-- precheck先确认payment_order.order_id为utf8mb4 VARCHAR(64)。
SET @fc_order_collation = (
  SELECT COLLATION_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='payment_order' AND COLUMN_NAME='order_id'
    AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64 AND CHARACTER_SET_NAME='utf8mb4'
);
SET @fc_alter = CONCAT(
  'ALTER TABLE membership_entitlement MODIFY COLUMN order_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE ',
  @fc_order_collation, ' NOT NULL'
);
PREPARE fc_statement FROM @fc_alter;
EXECUTE fc_statement;
DEALLOCATE PREPARE fc_statement;
