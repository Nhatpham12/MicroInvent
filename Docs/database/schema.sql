-- =====================================================================
--  ĐỒ ÁN INVENTORY MICROSERVICE (tên tạm) – THIẾT KẾ CSDL
--  Dựa trên SRS TigerNet v1.1 (mục 6), viết lại theo mô hình
--  "database per service" – MySQL 8.0, utf8mb4.
--
--  Nguyên tắc:
--   * Mỗi service sở hữu 1 database riêng, KHÔNG có FK chéo database.
--     Tham chiếu sang service khác chỉ lưu ID (+ snapshot tên khi cần in ấn).
--   * Bảng tra cứu nhỏ (VAT, trạng thái, loại giao dịch) chuyển thành ENUM.
--   * Bảng Inventory "gộp" của SRS được tách thành:
--       stock_balances (tồn theo cặp Sản phẩm–Kho, có version)
--       stock_items    (từng đơn vị có serial)
--       stock_movements(nhật ký biến động)
--     còn thông tin bán/thuê/thanh toán chuyển sang chứng từ ở order-service.
--   * Mọi bảng có created_at / updated_at; xóa mềm bằng is_deleted.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS auth_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS catalog_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS report_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =====================================================================
-- 1. AUTH-SERVICE  (auth_db) – người dùng, vai trò, phân quyền theo kho
-- =====================================================================
USE auth_db;

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

-- =====================================================================
-- 2. CATALOG-SERVICE (catalog_db) – sản phẩm, nhóm SP, NCC, khách hàng
-- =====================================================================
USE catalog_db;

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

-- =====================================================================
-- 3. INVENTORY-SERVICE (inventory_db) – kho, tồn kho, giữ hàng, kiểm kê
-- =====================================================================
USE inventory_db;

CREATE TABLE warehouses (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  code               VARCHAR(20)  NOT NULL UNIQUE,      -- HN01, HCM01
  name               VARCHAR(100) NOT NULL,
  type               ENUM('MAIN','BRANCH','WARRANTY','DAMAGED') NOT NULL,
  address            VARCHAR(255) NOT NULL,
  province           VARCHAR(100),
  phone              VARCHAR(20),
  email              VARCHAR(100),
  manager_user_id    INT NULL,                          -- ref auth_db.users.id
  status             ENUM('ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  is_default         BOOLEAN NOT NULL DEFAULT FALSE,    -- chỉ 1 kho TRUE (kiểm tra ở service)
  color              VARCHAR(7)  NULL,                  -- màu nhận diện trên header
  is_stocktake_locked BOOLEAN NOT NULL DEFAULT FALSE,   -- khóa giao dịch khi kiểm kê
  note               TEXT,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE warehouse_locations (                     -- Phase 2 (stretch goal)
  id           INT AUTO_INCREMENT PRIMARY KEY,
  warehouse_id INT NOT NULL,
  code         VARCHAR(30) NOT NULL,                    -- A-02-3 (Khu-Kệ-Ô)
  description  VARCHAR(255),
  UNIQUE KEY uq_location (warehouse_id, code),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

CREATE TABLE product_warehouse_settings (
  product_id   INT NOT NULL,                            -- ref catalog_db.products.id
  warehouse_id INT NOT NULL,
  min_stock    INT NOT NULL,
  PRIMARY KEY (product_id, warehouse_id),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

-- Tồn theo cặp (Sản phẩm, Kho). Tồn khả dụng = on_hand - hold - damaged
CREATE TABLE stock_balances (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id         INT NOT NULL,
  warehouse_id       INT NOT NULL,
  qty_on_hand        INT NOT NULL DEFAULT 0,            -- tồn thực (gồm hold + damaged)
  qty_hold           INT NOT NULL DEFAULT 0,
  qty_damaged        INT NOT NULL DEFAULT 0,
  qty_in_transit_in  INT NOT NULL DEFAULT 0,            -- đang chuyển đến kho này
  qty_rented_loaned  INT NOT NULL DEFAULT 0,            -- đang cho thuê/mượn (xuất từ kho này)
  avg_cost           DECIMAL(18,2) NOT NULL DEFAULT 0,  -- giá vốn bình quân
  version            INT NOT NULL DEFAULT 0,            -- optimistic locking
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_balance (product_id, warehouse_id),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CHECK (qty_on_hand >= 0 AND qty_hold >= 0 AND qty_damaged >= 0)
);

-- Từng đơn vị hàng có serial (Has_Serial = TRUE)
CREATE TABLE stock_items (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id     INT NOT NULL,
  warehouse_id   INT NULL,                              -- NULL khi IN_TRANSIT / đã bán / đang thuê
  last_warehouse_id INT NULL,                           -- kho gần nhất (để tra cứu bảo hành)
  location_id    INT NULL,
  serial         VARCHAR(255) NOT NULL UNIQUE,
  status         ENUM('AVAILABLE','HOLD','IN_TRANSIT','RENTED','LOANED','SOLD','DAMAGED','RETURNED_TO_SUPPLIER') NOT NULL DEFAULT 'AVAILABLE',
  cost_price     DECIMAL(18,2) NOT NULL DEFAULT 0,      -- giữ nguyên khi chuyển kho
  supplier_id    INT NULL,                              -- ref catalog_db.suppliers.id
  receipt_id     INT NULL,                              -- ref order_db.goods_receipts.id
  received_at    DATETIME NULL,
  current_ref_type VARCHAR(30) NULL,                    -- chứng từ đang giữ/đang thuê/đang chuyển
  current_ref_id   INT NULL,
  version        INT NOT NULL DEFAULT 0,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_items_prod_wh_status (product_id, warehouse_id, status),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  FOREIGN KEY (location_id) REFERENCES warehouse_locations(id)
);

CREATE TABLE stock_holds (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id      INT NOT NULL,
  warehouse_id    INT NOT NULL,
  stock_item_id   BIGINT NULL,                          -- giữ đúng serial (nếu có)
  qty             INT NOT NULL,
  customer_id     INT NULL,                             -- ref catalog_db.customers.id
  customer_name   VARCHAR(255) NULL,                    -- snapshot để hiển thị "giữ cho ai"
  held_by_user_id INT NOT NULL,
  ref_type        ENUM('MANUAL','EXPORT_SLIP','TRANSFER','RENTAL') NOT NULL DEFAULT 'MANUAL',
  ref_id          INT NULL,
  status          ENUM('ACTIVE','RELEASED','CONSUMED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  expires_at      DATETIME NULL,                        -- mặc định now + HOLD_DAYS
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_holds_active (status, expires_at),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  FOREIGN KEY (stock_item_id) REFERENCES stock_items(id)
);

CREATE TABLE stock_movements (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id    INT NOT NULL,
  warehouse_id  INT NOT NULL,
  stock_item_id BIGINT NULL,
  movement_type ENUM('IMPORT','EXPORT','RENT','LOAN','RETURN','TRANSFER_OUT','TRANSFER_IN',
                     'ADJUST','DAMAGED','WARRANTY','HOLD','RELEASE') NOT NULL,
  ref_type      ENUM('RECEIPT','EXPORT_SLIP','RENTAL','RENTAL_RETURN','SALES_RETURN','TRANSFER','STOCKTAKE','HOLD','MANUAL') NOT NULL,
  ref_id        INT NOT NULL,
  qty_change    INT NOT NULL,                           -- + / -
  qty_before    INT NOT NULL,
  qty_after     INT NOT NULL,
  unit_cost     DECIMAL(18,2) NULL,
  user_id       INT NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_mov_wh_time (warehouse_id, created_at),
  INDEX idx_mov_ref (ref_type, ref_id),
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  FOREIGN KEY (stock_item_id) REFERENCES stock_items(id)
);

CREATE TABLE stocktakes (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(30) NOT NULL UNIQUE,         -- PKK-2026-0001
  warehouse_id     INT NOT NULL,
  status           ENUM('DRAFT','COUNTING','PENDING_APPROVAL','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  lock_transactions BOOLEAN NOT NULL DEFAULT FALSE,
  created_by       INT NOT NULL,
  approved_by      INT NULL,
  started_at       DATETIME NULL,
  completed_at     DATETIME NULL,
  note             TEXT,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

CREATE TABLE stocktake_items (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  stocktake_id  INT NOT NULL,
  product_id    INT NOT NULL,
  system_qty    INT NOT NULL,                           -- snapshot lúc bắt đầu (không tính hàng đang chuyển đến)
  actual_qty    INT NULL,
  diff_qty      INT GENERATED ALWAYS AS (actual_qty - system_qty) STORED,
  scanned_serials JSON NULL,
  note          VARCHAR(255),
  UNIQUE KEY uq_st_item (stocktake_id, product_id),
  FOREIGN KEY (stocktake_id) REFERENCES stocktakes(id)
);

-- Chống xử lý trùng request nội bộ / event (idempotency)
CREATE TABLE processed_requests (
  idempotency_key VARCHAR(100) PRIMARY KEY,
  response_json   JSON NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- 4. ORDER-SERVICE (order_db) – chứng từ: nhập, xuất, thuê/mượn,
--    đổi trả, chuyển kho, thanh toán & công nợ
-- =====================================================================
USE order_db;

CREATE TABLE doc_sequences (                           -- sinh mã PNK-2026-0001, PXK-, PCK-, PTH-, PDT-
  prefix   VARCHAR(10) NOT NULL,
  year     INT NOT NULL,
  last_no  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, year)
);

-- ---------- Phiếu nhập kho (PNK) ----------
CREATE TABLE goods_receipts (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(30) NOT NULL UNIQUE,
  warehouse_id     INT NOT NULL,                        -- kho nhận (bắt buộc)
  source_type      ENUM('SUPPLIER','RETAIL_CUSTOMER','OTHER') NOT NULL DEFAULT 'SUPPLIER',
  supplier_id      INT NULL,
  customer_id      INT NULL,                            -- khách lẻ bán lại hàng
  partner_snapshot JSON NULL,                           -- tên, MST, địa chỉ tại thời điểm lập
  receipt_date     DATETIME NOT NULL,
  status           ENUM('DRAFT','CONFIRMED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  contract_no      VARCHAR(100),                        -- số HĐ / đơn đặt hàng
  tracking_no      VARCHAR(100),                        -- mã vận đơn
  handover_doc_url VARCHAR(500),                        -- biên bản bàn giao NCC
  bank_payment_ref VARCHAR(100),
  vat_rate         ENUM('NONE','0','5','8','10') NOT NULL DEFAULT 'NONE',
  subtotal         DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount     DECIMAL(18,2) NOT NULL DEFAULT 0,
  paid_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
  payment_status   ENUM('UNPAID','PARTIAL','PAID') NOT NULL DEFAULT 'UNPAID',
  note             TEXT,
  created_by       INT NOT NULL,
  confirmed_by     INT NULL,
  confirmed_at     DATETIME NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_gr_wh_date (warehouse_id, receipt_date),
  INDEX idx_gr_supplier (supplier_id)
);

CREATE TABLE goods_receipt_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  receipt_id   INT NOT NULL,
  product_id   INT NOT NULL,
  product_snapshot JSON NOT NULL,                       -- code, name, unit
  qty          INT NOT NULL CHECK (qty > 0),
  unit_price   DECIMAL(18,2) NOT NULL,
  line_total   DECIMAL(18,2) NOT NULL,
  serials      JSON NULL,                               -- ["SN1","SN2"] khi has_serial
  condition_note VARCHAR(255),
  FOREIGN KEY (receipt_id) REFERENCES goods_receipts(id)
);

-- ---------- Phiếu xuất kho / bán hàng (PXK) ----------
CREATE TABLE export_slips (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(30) NOT NULL UNIQUE,
  export_type      ENUM('SALE','DAMAGED','WARRANTY','RETURN_SUPPLIER','OTHER') NOT NULL DEFAULT 'SALE',
  warehouse_id     INT NOT NULL,                        -- kho xuất (bắt buộc)
  customer_id      INT NULL,
  customer_snapshot JSON NULL,                          -- tên, địa chỉ, MST, người nhận
  export_date      DATETIME NULL,
  status           ENUM('PENDING','CONFIRMED','EXPORTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  delivery_method  ENUM('PICKUP','CARRIER') NULL,
  tracking_no      VARCHAR(100),
  vat_rate         ENUM('NONE','0','5','8','10') NOT NULL DEFAULT 'NONE',
  subtotal         DECIMAL(18,2) NOT NULL DEFAULT 0,
  vat_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount     DECIMAL(18,2) NOT NULL DEFAULT 0,
  deposit_amount   DECIMAL(18,2) NOT NULL DEFAULT 0,
  paid_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
  payment_status   ENUM('UNPAID','PARTIAL','PAID') NOT NULL DEFAULT 'UNPAID',
  due_date         DATE NULL,                           -- hạn thanh toán (công nợ)
  sales_user_id    INT NULL,                            -- nhân viên bán (báo cáo doanh thu NV)
  split_from_id    INT NULL,                            -- tách phiếu theo kho
  note             TEXT,
  created_by       INT NOT NULL,
  confirmed_by     INT NULL,
  exported_by      INT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_es_wh_date (warehouse_id, export_date),
  INDEX idx_es_customer (customer_id, payment_status),
  FOREIGN KEY (split_from_id) REFERENCES export_slips(id)
);

CREATE TABLE export_slip_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  slip_id          INT NOT NULL,
  product_id       INT NOT NULL,
  product_snapshot JSON NOT NULL,
  qty              INT NOT NULL CHECK (qty > 0),
  unit_price       DECIMAL(18,2) NOT NULL,
  unit_cost        DECIMAL(18,2) NULL,                  -- giá vốn lúc xuất (tính lợi nhuận)
  line_total       DECIMAL(18,2) NOT NULL,
  serials          JSON NULL,
  warranty_months  INT NULL,
  warranty_until   DATE NULL,
  FOREIGN KEY (slip_id) REFERENCES export_slips(id)
);

-- ---------- Cho thuê / cho mượn ----------
CREATE TABLE rental_contracts (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(30) NOT NULL UNIQUE,         -- PCT- (thuê) / PCM- (mượn)
  rental_type      ENUM('RENT','LOAN') NOT NULL,
  warehouse_id     INT NOT NULL,                        -- kho xuất
  customer_id      INT NOT NULL,
  customer_snapshot JSON NULL,
  start_date       DATE NOT NULL,
  due_date         DATE NOT NULL,
  status           ENUM('ACTIVE','PARTIAL_RETURNED','RETURNED','OVERDUE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  deposit_amount   DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_rent       DECIMAL(18,2) NOT NULL DEFAULT 0,
  penalty_amount   DECIMAL(18,2) NOT NULL DEFAULT 0,
  paid_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
  payment_status   ENUM('UNPAID','PARTIAL','PAID') NOT NULL DEFAULT 'UNPAID',
  note             TEXT,
  created_by       INT NOT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_rc_due (status, due_date)
);

CREATE TABLE rental_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id      INT NOT NULL,
  product_id       INT NOT NULL,
  product_snapshot JSON NOT NULL,
  qty              INT NOT NULL CHECK (qty > 0),
  returned_qty     INT NOT NULL DEFAULT 0,
  daily_rate       DECIMAL(18,2) NOT NULL DEFAULT 0,    -- = 0 với hàng mượn
  serials          JSON NULL,
  FOREIGN KEY (contract_id) REFERENCES rental_contracts(id)
);

CREATE TABLE rental_returns (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  code                VARCHAR(30) NOT NULL UNIQUE,
  contract_id         INT NOT NULL,
  return_warehouse_id INT NOT NULL,                     -- có thể khác kho xuất
  return_date         DATE NOT NULL,
  overdue_days        INT NOT NULL DEFAULT 0,
  penalty_amount      DECIMAL(18,2) NOT NULL DEFAULT 0, -- quá hạn >= 10 ngày: ngày quá hạn x giá thuê/ngày
  deposit_deducted    DECIMAL(18,2) NOT NULL DEFAULT 0,
  note                TEXT,
  received_by         INT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (contract_id) REFERENCES rental_contracts(id)
);

CREATE TABLE rental_return_items (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  return_id       INT NOT NULL,
  rental_item_id  BIGINT NOT NULL,
  qty             INT NOT NULL CHECK (qty > 0),
  serials         JSON NULL,
  item_condition  ENUM('GOOD','DAMAGED') NOT NULL DEFAULT 'GOOD',
  FOREIGN KEY (return_id) REFERENCES rental_returns(id),
  FOREIGN KEY (rental_item_id) REFERENCES rental_items(id)
);

-- ---------- Đổi / trả hàng đã bán ----------
CREATE TABLE sales_returns (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  code                VARCHAR(30) NOT NULL UNIQUE,      -- PDT-2026-0001
  export_slip_id      INT NOT NULL,                     -- phiếu bán gốc
  return_warehouse_id INT NOT NULL,                     -- kho nhận trả (bất kỳ)
  return_type         ENUM('RETURN','EXCHANGE_HIGHER_EQUAL','EXCHANGE_LOWER') NOT NULL,
  days_since_delivery INT NOT NULL,
  fee_rate            DECIMAL(5,2) NOT NULL DEFAULT 0,  -- theo bảng chính sách 3.8
  fee_amount          DECIMAL(18,2) NOT NULL DEFAULT 0,
  refund_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
  new_export_slip_id  INT NULL,                         -- phiếu xuất hàng đổi mới
  status              ENUM('DRAFT','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  reason              VARCHAR(255),
  created_by          INT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (export_slip_id) REFERENCES export_slips(id),
  FOREIGN KEY (new_export_slip_id) REFERENCES export_slips(id)
);

CREATE TABLE sales_return_items (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  return_id       INT NOT NULL,
  export_item_id  BIGINT NOT NULL,
  product_id      INT NOT NULL,
  qty             INT NOT NULL CHECK (qty > 0),
  serials         JSON NULL,
  item_condition  ENUM('GOOD','DAMAGED') NOT NULL DEFAULT 'GOOD',
  FOREIGN KEY (return_id) REFERENCES sales_returns(id),
  FOREIGN KEY (export_item_id) REFERENCES export_slip_items(id)
);

-- ---------- Phiếu chuyển kho (PCK) ----------
CREATE TABLE stock_transfers (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  code              VARCHAR(30) NOT NULL UNIQUE,        -- PCK-2026-0001
  from_warehouse_id INT NOT NULL,
  to_warehouse_id   INT NOT NULL,
  status            ENUM('DRAFT','PENDING','APPROVED','IN_TRANSIT','RECEIVED','DISCREPANCY','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  created_by        INT NOT NULL,
  approved_by       INT NULL,
  shipped_by        INT NULL,
  received_by       INT NULL,
  shipped_at        DATETIME NULL,
  expected_date     DATE NULL,
  received_at       DATETIME NULL,
  shipping_method   ENUM('INTERNAL','CARRIER') NULL,
  tracking_no       VARCHAR(100),
  shipping_cost     DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_value       DECIMAL(18,2) NOT NULL DEFAULT 0,   -- theo giá vốn (để áp ngưỡng bỏ qua duyệt)
  reason            VARCHAR(255),
  reject_reason     VARCHAR(255),
  note              TEXT,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tf_status (status, expected_date),
  CHECK (from_warehouse_id <> to_warehouse_id)
);

CREATE TABLE stock_transfer_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  transfer_id      INT NOT NULL,
  product_id       INT NOT NULL,
  product_snapshot JSON NOT NULL,
  qty_sent         INT NOT NULL CHECK (qty_sent > 0),
  qty_received     INT NULL,
  serials_sent     JSON NULL,
  serials_received JSON NULL,
  condition_note   VARCHAR(255),
  FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id)
);

CREATE TABLE transfer_discrepancies (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  transfer_id      INT NOT NULL,
  transfer_item_id BIGINT NOT NULL,
  qty_diff         INT NOT NULL,                        -- thiếu (<0) / hỏng
  diff_type        ENUM('MISSING','DAMAGED','EXCESS') NOT NULL,
  resolution       ENUM('PENDING','RESEND','LOSS','RETURN_TO_SOURCE') NOT NULL DEFAULT 'PENDING',
  resolved_by      INT NULL,
  resolved_at      DATETIME NULL,
  note             VARCHAR(255),
  FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id),
  FOREIGN KEY (transfer_item_id) REFERENCES stock_transfer_items(id)
);

-- ---------- Thanh toán & công nợ ----------
CREATE TABLE payments (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ref_type    ENUM('RECEIPT','EXPORT_SLIP','RENTAL','SALES_RETURN') NOT NULL,
  ref_id      INT NOT NULL,
  direction   ENUM('IN','OUT') NOT NULL,                -- IN: thu khách, OUT: trả NCC / hoàn tiền
  amount      DECIMAL(18,2) NOT NULL CHECK (amount > 0),
  method      ENUM('CASH','BANK_TRANSFER','DEPOSIT_DEDUCT') NOT NULL,
  bank_ref    VARCHAR(100),
  paid_at     DATETIME NOT NULL,
  note        VARCHAR(255),
  created_by  INT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_pay_ref (ref_type, ref_id)
);

-- Transactional outbox: ghi event cùng transaction, worker đẩy lên RabbitMQ
CREATE TABLE outbox_events (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type   VARCHAR(80) NOT NULL,                    -- receipt.confirmed, export.exported, transfer.shipped...
  aggregate_id INT NOT NULL,
  payload      JSON NOT NULL,
  status       ENUM('PENDING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  retry_count  INT NOT NULL DEFAULT 0,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at DATETIME NULL,
  INDEX idx_outbox_status (status, created_at)
);

-- =====================================================================
-- 5. REPORT-SERVICE (report_db) – báo cáo (read model), cảnh báo,
--    nhật ký thao tác, xuất file, backup
-- =====================================================================
USE report_db;

CREATE TABLE notifications (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL,
  warehouse_id INT NULL,
  type        ENUM('LOW_STOCK','RENT_DUE','LOAN_DUE','RENT_OVERDUE','APPROVAL_PENDING','TRANSFER_OVERDUE',
                   'TRANSFER_DISCREPANCY','HOLD_EXPIRING','DEBT_OVERDUE','SYNC_CONFLICT') NOT NULL,
  title       VARCHAR(255) NOT NULL,
  message     TEXT,
  ref_type    VARCHAR(30),
  ref_id      INT,
  is_read     BOOLEAN NOT NULL DEFAULT FALSE,
  is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_noti_user (user_id, is_read, created_at)
);

CREATE TABLE audit_logs (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      INT NULL,
  username     VARCHAR(50),
  warehouse_id INT NULL,
  service      VARCHAR(30) NOT NULL,
  action       VARCHAR(60) NOT NULL,                    -- CREATE, UPDATE, CONFIRM, APPROVE, LOGIN...
  entity       VARCHAR(60) NOT NULL,
  entity_id    VARCHAR(50),
  before_data  JSON NULL,
  after_data   JSON NULL,
  ip_address   VARCHAR(45),
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_time (created_at),
  INDEX idx_audit_user (user_id, created_at),
  INDEX idx_audit_wh (warehouse_id, created_at)
);

-- Read model doanh thu: cập nhật từ event export.exported / rental.* / sales_return.completed
CREATE TABLE fact_sales (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  sale_date     DATE NOT NULL,
  warehouse_id  INT NOT NULL,
  product_id    INT NOT NULL,
  product_group_id INT NULL,
  customer_id   INT NULL,
  sales_user_id INT NULL,
  source_type   ENUM('SALE','RENT','RETURN_FEE','REFUND') NOT NULL,
  source_id     INT NOT NULL,
  qty           INT NOT NULL,
  revenue       DECIMAL(18,2) NOT NULL,
  cost          DECIMAL(18,2) NOT NULL DEFAULT 0,
  INDEX idx_fs_date_wh (sale_date, warehouse_id),
  INDEX idx_fs_product (product_id, sale_date)
);

-- Read model nhập/xuất/chuyển theo ngày (từ event stock.changed)
CREATE TABLE fact_stock_movements (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  movement_date DATE NOT NULL,
  warehouse_id  INT NOT NULL,
  product_id    INT NOT NULL,
  supplier_id   INT NULL,
  movement_type VARCHAR(20) NOT NULL,
  qty           INT NOT NULL,
  value         DECIMAL(18,2) NOT NULL DEFAULT 0,
  INDEX idx_fsm_date_wh (movement_date, warehouse_id, movement_type)
);

-- Snapshot tồn cuối ngày (cron 23:59) để xem tồn theo thời điểm
CREATE TABLE stock_snapshots (
  snapshot_date DATE NOT NULL,
  warehouse_id  INT NOT NULL,
  product_id    INT NOT NULL,
  qty_on_hand   INT NOT NULL,
  qty_available INT NOT NULL,
  qty_in_transit INT NOT NULL DEFAULT 0,
  qty_rented_loaned INT NOT NULL DEFAULT 0,
  stock_value   DECIMAL(18,2) NOT NULL,
  PRIMARY KEY (snapshot_date, warehouse_id, product_id)
);

CREATE TABLE export_jobs (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_type  VARCHAR(50) NOT NULL,
  params       JSON NOT NULL,
  format       ENUM('XLSX','PDF','DOCX','XML') NOT NULL,
  status       ENUM('QUEUED','RUNNING','DONE','FAILED') NOT NULL DEFAULT 'QUEUED',
  file_url     VARCHAR(500),
  error        TEXT,
  requested_by INT NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at  DATETIME NULL
);

CREATE TABLE backup_logs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  trigger_type ENUM('AUTO','MANUAL') NOT NULL,
  status      ENUM('RUNNING','SUCCESS','FAILED') NOT NULL,
  file_path   VARCHAR(500),
  size_bytes  BIGINT,
  started_at  DATETIME NOT NULL,
  finished_at DATETIME NULL,
  triggered_by INT NULL,
  error       TEXT
);

CREATE TABLE processed_events (                        -- consumer idempotency
  event_id     VARCHAR(100) PRIMARY KEY,
  processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- SEED DỮ LIỆU MẶC ĐỊNH
-- =====================================================================
USE auth_db;
INSERT INTO roles (code, name, all_warehouses) VALUES
 ('ADMIN','Quản trị hệ thống',TRUE),('MANAGER','Nhà quản lý',TRUE),
 ('ACCOUNTANT','Kế toán',TRUE),('STOREKEEPER','Thủ kho',FALSE),('SALES','Nhân viên bán hàng',FALSE);
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('HOLD_DAYS','3','Số ngày giữ hàng mặc định'),
 ('TRANSFER_OVERDUE_DAYS','3','Số ngày chuyển kho trước khi cảnh báo quá hạn'),
 ('TRANSFER_APPROVAL_THRESHOLD','0','Giá trị phiếu chuyển dưới ngưỡng này được bỏ qua duyệt (0 = luôn duyệt)'),
 ('RENT_DUE_WARNING_DAYS','7','Cảnh báo trước khi hết hạn thuê/mượn'),
 ('RENT_PENALTY_FROM_DAYS','10','Tính phạt khi quá hạn từ N ngày'),
 ('DEBT_OVERDUE_DAYS','30','Công nợ quá hạn'),
 ('MAX_FAILED_LOGIN','5','Số lần đăng nhập sai trước khi khóa'),
 ('LOCK_MINUTES','5','Thời gian khóa tạm thời');
