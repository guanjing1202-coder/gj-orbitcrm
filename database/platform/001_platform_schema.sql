CREATE DATABASE IF NOT EXISTS orbit_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE orbit_platform;

CREATE TABLE IF NOT EXISTS platform_tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_code VARCHAR(64) NOT NULL UNIQUE,
  tenant_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PROVISIONING',
  contact_name VARCHAR(64),
  contact_email VARCHAR(128),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_tenant_domain (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  domain VARCHAR(255) NOT NULL UNIQUE,
  verify_token VARCHAR(128) NOT NULL,
  verify_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  ssl_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant_domain_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_tenant_database (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  tenant_code VARCHAR(64) NOT NULL UNIQUE,
  db_name VARCHAR(128) NOT NULL,
  jdbc_url VARCHAR(512) NOT NULL,
  username VARCHAR(128) NOT NULL,
  password_cipher VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant_database_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_code VARCHAR(64) NOT NULL UNIQUE,
  plan_name VARCHAR(128) NOT NULL,
  billing_cycle VARCHAR(32) NOT NULL,
  price_cent BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_plan_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  feature_key VARCHAR(128) NOT NULL,
  feature_value VARCHAR(255) NOT NULL,
  value_type VARCHAR(32) NOT NULL DEFAULT 'NUMBER',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_feature (plan_id, feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  trial_end_time DATETIME,
  grace_days INT NOT NULL DEFAULT 7,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_subscription_tenant (tenant_id),
  INDEX idx_subscription_status_end (status, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  tenant_id BIGINT NOT NULL,
  subscription_id BIGINT,
  plan_id BIGINT,
  order_type VARCHAR(32) NOT NULL DEFAULT 'RENEW',
  period_months INT NOT NULL DEFAULT 1,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  paid_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  payment_channel VARCHAR(32) NOT NULL,
  payment_no VARCHAR(128),
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  paid_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_payment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT,
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(64),
  detail_json TEXT,
  ip VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO platform_plan (plan_code, plan_name, billing_cycle, price_cent)
VALUES ('starter', 'Starter', 'MONTH', 0),
       ('professional', 'Professional', 'MONTH', 9900),
       ('enterprise', 'Enterprise', 'YEAR', 99900)
ON DUPLICATE KEY UPDATE plan_name = VALUES(plan_name);

INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_users', '5', 'NUMBER' FROM platform_plan WHERE plan_code = 'starter'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_customers', '1000', 'NUMBER' FROM platform_plan WHERE plan_code = 'starter'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_leads', '3000', 'NUMBER' FROM platform_plan WHERE plan_code = 'starter'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'file_storage_gb', '1', 'NUMBER' FROM platform_plan WHERE plan_code = 'starter'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'custom_domain', 'false', 'BOOLEAN' FROM platform_plan WHERE plan_code = 'starter'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);

INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_users', '30', 'NUMBER' FROM platform_plan WHERE plan_code = 'professional'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_customers', '30000', 'NUMBER' FROM platform_plan WHERE plan_code = 'professional'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_leads', '-1', 'NUMBER' FROM platform_plan WHERE plan_code = 'professional'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'file_storage_gb', '10', 'NUMBER' FROM platform_plan WHERE plan_code = 'professional'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'custom_domain', 'true', 'BOOLEAN' FROM platform_plan WHERE plan_code = 'professional'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);

INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_users', '-1', 'NUMBER' FROM platform_plan WHERE plan_code = 'enterprise'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_customers', '-1', 'NUMBER' FROM platform_plan WHERE plan_code = 'enterprise'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'max_leads', '-1', 'NUMBER' FROM platform_plan WHERE plan_code = 'enterprise'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'file_storage_gb', '100', 'NUMBER' FROM platform_plan WHERE plan_code = 'enterprise'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
INSERT INTO platform_plan_feature (plan_id, feature_key, feature_value, value_type)
SELECT id, 'custom_domain', 'true', 'BOOLEAN' FROM platform_plan WHERE plan_code = 'enterprise'
ON DUPLICATE KEY UPDATE feature_value = VALUES(feature_value), value_type = VALUES(value_type);
