-- catalog-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE product_groups (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(50)  NOT NULL UNIQUE,            -- CISCO_ROUTER, CISCO_SFP, FIREWALL...
  name        VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255),
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE products (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  group_id          INT NOT NULL,
  code              VARCHAR(50)  NOT NULL UNIQUE,
  name              VARCHAR(255) NOT NULL,
  unit              VARCHAR(20)  NOT NULL,             -- cái, bộ, mét...
  default_price_in  DECIMAL(18,2) NULL,
  default_price_out DECIMAL(18,2) NULL,                -- giá bán biểu kiến
  default_rent_price_per_day DECIMAL(18,2) NULL,
  min_stock         INT NULL,                          -- ngưỡng chung khi kho không đặt riêng
  has_serial        BOOLEAN NOT NULL DEFAULT FALSE,
  warranty_months   INT NULL,
  image_url         VARCHAR(500) NULL,
  description       TEXT NULL,
  is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (group_id) REFERENCES product_groups(id),
  FULLTEXT INDEX ft_products_name (name)
);

CREATE TABLE suppliers (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  code              VARCHAR(30)  NOT NULL UNIQUE,
  name              VARCHAR(255) NOT NULL,
  tax_code          VARCHAR(20)  NOT NULL UNIQUE,
  address           VARCHAR(255),
  phone_fax         VARCHAR(50),
  email             VARCHAR(100),
  bank_account_no   VARBINARY(255) NULL,               -- mã hóa AES-256 ở tầng ứng dụng
  bank_account_name VARCHAR(100),
  bank_name         VARCHAR(100),
  note              TEXT,
  is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE customers (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(30)  NOT NULL UNIQUE,
  name          VARCHAR(255) NOT NULL,
  customer_type ENUM('RETAIL','BUSINESS') NOT NULL DEFAULT 'RETAIL',
  tax_code      VARCHAR(20),
  id_card_no    VARBINARY(255) NULL,                   -- CCCD (khách bán lẻ bán lại hàng), mã hóa
  address       VARCHAR(255),
  phone         VARCHAR(20),
  email         VARCHAR(100),
  note          TEXT,
  is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_customers_phone (phone)
);
