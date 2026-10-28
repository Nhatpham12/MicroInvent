-- identity-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE roles (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(30)  NOT NULL UNIQUE,      -- ADMIN, MANAGER, ACCOUNTANT, STOREKEEPER, SALES
  name        VARCHAR(100) NOT NULL,
  all_warehouses BOOLEAN NOT NULL DEFAULT FALSE  -- TRUE: xem mọi kho (ADMIN, MANAGER, ACCOUNTANT)
);

CREATE TABLE permissions (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(60)  NOT NULL UNIQUE,      -- vd: receipt.create, transfer.approve
  description VARCHAR(255)
);

CREATE TABLE role_permissions (
  role_id       INT NOT NULL,
  permission_id INT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES roles(id),
  FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE users (
  id                   INT AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(50)  NOT NULL UNIQUE,
  email                VARCHAR(100) NOT NULL UNIQUE,
  password_hash        VARCHAR(255) NOT NULL,           -- bcrypt
  full_name            VARCHAR(100) NOT NULL,
  phone                VARCHAR(20),
  role_id              INT NOT NULL,
  default_warehouse_id INT NULL,                        -- ref inventory_db.warehouses.id
  is_active            BOOLEAN  NOT NULL DEFAULT TRUE,
  failed_login_count   INT      NOT NULL DEFAULT 0,
  locked_until         DATETIME NULL,                   -- khóa 5 phút sau 5 lần sai
  last_login_at        DATETIME NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE user_warehouses (
  user_id      INT NOT NULL,
  warehouse_id INT NOT NULL,                           -- ref inventory_db.warehouses.id
  can_write    BOOLEAN NOT NULL DEFAULT TRUE,          -- FALSE = chỉ xem
  PRIMARY KEY (user_id, warehouse_id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE refresh_tokens (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL,
  token_hash  VARCHAR(255) NOT NULL UNIQUE,
  user_agent  VARCHAR(255),
  ip_address  VARCHAR(45),
  expires_at  DATETIME NOT NULL,
  revoked_at  DATETIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE password_otps (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL,
  otp_hash    VARCHAR(255) NOT NULL,
  attempts    INT NOT NULL DEFAULT 0,
  expires_at  DATETIME NOT NULL,
  used_at     DATETIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE login_histories (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NULL,
  username    VARCHAR(50) NOT NULL,
  success     BOOLEAN NOT NULL,
  ip_address  VARCHAR(45),
  user_agent  VARCHAR(255),
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_login_user_time (user_id, created_at)
);

CREATE TABLE system_settings (                         -- cấu hình: số ngày giữ hàng, ngưỡng duyệt chuyển kho...
  setting_key   VARCHAR(80) PRIMARY KEY,               -- HOLD_DAYS=3, TRANSFER_OVERDUE_DAYS=3, TRANSFER_APPROVAL_THRESHOLD, RENT_PENALTY_GRACE_DAYS=10
  setting_value VARCHAR(255) NOT NULL,
  description   VARCHAR(255),
  updated_by    INT NULL,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
