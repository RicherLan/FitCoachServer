-- 在部署新版 Server 前执行；仅新增权益账本，不改写既有会员到期时间。
CREATE TABLE IF NOT EXISTS membership_entitlement (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NULL,
  plan_code VARCHAR(32) NULL,
  duration_days INT NOT NULL DEFAULT 0,
  granted_at DATETIME(6) NULL,
  revoked BIT(1) NOT NULL DEFAULT b'0',
  baseline_expires_at DATETIME(6) NULL,
  UNIQUE KEY uk_membership_entitlement_order (order_id),
  KEY idx_membership_entitlement_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 保留旧会员为固定基线，禁止用现在的套餐天数猜测历史权益。
INSERT INTO membership_entitlement
  (created_at, updated_at, order_id, user_id, plan_id, plan_code, duration_days, granted_at, revoked, baseline_expires_at)
SELECT NOW(6), NOW(6), CONCAT('LEGACY:', m.user_id), m.user_id, m.plan_id, m.plan_code, 0, m.activated_at, b'0', m.expires_at
FROM user_membership m
WHERE NOT EXISTS (SELECT 1 FROM membership_entitlement e WHERE e.user_id = m.user_id);
