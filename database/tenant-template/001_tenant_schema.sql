CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(64),
  email VARCHAR(128),
  phone VARCHAR(32),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(128) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  menu_code VARCHAR(64) NOT NULL UNIQUE,
  menu_name VARCHAR(128) NOT NULL,
  path VARCHAR(255),
  sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL UNIQUE,
  permission_name VARCHAR(128) NOT NULL,
  resource_type VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  dept_name VARCHAR(128) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dept_name (dept_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(64) NOT NULL,
  login_result VARCHAR(32) NOT NULL,
  ip VARCHAR(64),
  user_agent VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_login_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_lead (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lead_name VARCHAR(128) NOT NULL,
  company_name VARCHAR(128),
  phone VARCHAR(32),
  email VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'NEW',
  owner_user_id BIGINT,
  source VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lead_owner_status (owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_name VARCHAR(128) NOT NULL,
  customer_type VARCHAR(32),
  phone VARCHAR(32),
  email VARCHAR(128),
  address VARCHAR(255),
  owner_user_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customer_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_contact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  contact_name VARCHAR(128) NOT NULL,
  title VARCHAR(128),
  phone VARCHAR(32),
  email VARCHAR(128),
  is_primary TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_contact_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_pipeline (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pipeline_name VARCHAR(128) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_pipeline_name (pipeline_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_pipeline_stage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pipeline_id BIGINT NOT NULL,
  stage_name VARCHAR(128) NOT NULL,
  win_probability INT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  INDEX idx_stage_pipeline (pipeline_id),
  UNIQUE KEY uk_stage_pipeline_name (pipeline_id, stage_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_deal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  deal_name VARCHAR(128) NOT NULL,
  customer_id BIGINT,
  pipeline_id BIGINT NOT NULL,
  stage_id BIGINT NOT NULL,
  amount_cent BIGINT NOT NULL DEFAULT 0,
  expected_close_date DATE,
  owner_user_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_deal_stage (pipeline_id, stage_id),
  INDEX idx_deal_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  related_type VARCHAR(32),
  related_id BIGINT,
  assignee_user_id BIGINT,
  due_time DATETIME,
  remind_time DATETIME,
  remind_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'TODO',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_task_assignee_status (assignee_user_id, status),
  INDEX idx_task_remind (remind_status, remind_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_follow_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  related_type VARCHAR(32) NOT NULL,
  related_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  next_follow_time DATETIME,
  creator_user_id BIGINT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_follow_related (related_type, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_name VARCHAR(64) NOT NULL UNIQUE,
  color VARCHAR(32),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crm_customer_tag (
  customer_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (customer_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(64),
  biz_id BIGINT,
  bucket_name VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128),
  size_bytes BIGINT NOT NULL DEFAULT 0,
  uploader_user_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file_biz (biz_type, biz_id),
  INDEX idx_file_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  notice_type VARCHAR(32) NOT NULL,
  sender_user_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_notice_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_notice_receiver (
  notice_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_time DATETIME,
  PRIMARY KEY (notice_id, user_id),
  INDEX idx_notice_receiver_user_read (user_id, read_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_openapi_key (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  key_name VARCHAR(128) NOT NULL,
  key_prefix VARCHAR(32) NOT NULL,
  key_hash VARCHAR(128) NOT NULL,
  scopes VARCHAR(512) NOT NULL,
  creator_user_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_used_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_openapi_key_hash (key_hash),
  INDEX idx_openapi_key_prefix_status (key_prefix, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(64),
  detail_json TEXT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_role (role_code, role_name)
VALUES ('tenant_admin', 'Tenant Admin'),
       ('sales_manager', 'Sales Manager'),
       ('sales', 'Sales'),
       ('viewer', 'Viewer')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO crm_pipeline (pipeline_name, is_default)
VALUES ('Default Pipeline', 1)
ON DUPLICATE KEY UPDATE is_default = VALUES(is_default);

INSERT INTO sys_dept (parent_id, dept_name, sort_order)
VALUES (0, 'Head Office', 1)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), status = 'ACTIVE';

INSERT INTO sys_menu (parent_id, menu_code, menu_name, path, sort_order)
VALUES (0, 'dashboard', 'Dashboard', '/dashboard', 1),
       (0, 'leads', 'Leads', '/crm/leads', 2),
       (0, 'customers', 'Customers', '/crm/customers', 3),
       (0, 'deals', 'Deals', '/crm/deals', 4),
       (0, 'tasks', 'Tasks', '/tasks', 5),
       (0, 'files', 'Files', '/files', 6),
       (0, 'messages', 'Messages', '/messages', 7),
       (0, 'reports', 'Reports', '/reports', 8),
       (0, 'billing', 'Billing', '/billing', 9),
       (0, 'openapi', 'OpenAPI', '/openapi', 10),
       (0, 'system', 'System', '/system', 99)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), path = VALUES(path), sort_order = VALUES(sort_order);

INSERT INTO sys_permission (permission_code, permission_name, resource_type)
VALUES ('crm:lead:manage', 'Manage Leads', 'API'),
       ('crm:customer:manage', 'Manage Customers', 'API'),
       ('crm:contact:manage', 'Manage Contacts', 'API'),
       ('crm:deal:manage', 'Manage Deals', 'API'),
       ('crm:follow:manage', 'Manage Follow Records', 'API'),
       ('task:manage', 'Manage Tasks', 'API'),
       ('report:dashboard:view', 'View Dashboard Reports', 'API'),
       ('tenant:domain:manage', 'Manage Tenant Domains', 'API'),
       ('file:manage', 'Manage Files', 'API'),
       ('message:notice:view', 'View Notices', 'API'),
       ('message:notice:manage', 'Manage Notices', 'API'),
       ('billing:subscription:manage', 'Manage Subscription Billing', 'API'),
       ('openapi:key:manage', 'Manage OpenAPI Keys', 'API'),
       ('system:user:manage', 'Manage Users', 'API'),
       ('system:role:manage', 'Manage Roles', 'API'),
       ('system:audit:view', 'View Operation Logs', 'API')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), resource_type = VALUES(resource_type);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m WHERE r.role_code = 'tenant_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p WHERE r.role_code = 'tenant_admin'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'sales_manager'
  AND p.permission_code IN ('crm:lead:manage', 'crm:customer:manage', 'crm:contact:manage', 'crm:deal:manage', 'crm:follow:manage', 'task:manage', 'report:dashboard:view', 'file:manage', 'message:notice:view', 'message:notice:manage')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.role_code = 'sales'
  AND p.permission_code IN ('crm:lead:manage', 'crm:customer:manage', 'crm:contact:manage', 'crm:deal:manage', 'crm:follow:manage', 'task:manage', 'report:dashboard:view', 'file:manage', 'message:notice:view')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m
WHERE r.role_code = 'sales_manager'
  AND m.menu_code IN ('dashboard', 'leads', 'customers', 'deals', 'tasks', 'files', 'messages', 'reports')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m
WHERE r.role_code = 'sales'
  AND m.menu_code IN ('dashboard', 'leads', 'customers', 'deals', 'tasks', 'files', 'messages', 'reports')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO crm_pipeline_stage (pipeline_id, stage_name, win_probability, sort_order)
SELECT id, 'Qualified', 20, 1 FROM crm_pipeline WHERE is_default = 1
UNION ALL
SELECT id, 'Proposal', 50, 2 FROM crm_pipeline WHERE is_default = 1
UNION ALL
SELECT id, 'Negotiation', 75, 3 FROM crm_pipeline WHERE is_default = 1
UNION ALL
SELECT id, 'Won', 100, 4 FROM crm_pipeline WHERE is_default = 1
ON DUPLICATE KEY UPDATE win_probability = VALUES(win_probability), sort_order = VALUES(sort_order);
