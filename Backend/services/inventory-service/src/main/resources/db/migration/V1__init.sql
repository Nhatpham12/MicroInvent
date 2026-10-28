-- inventory-service – schema khởi tạo (tách từ Docs/database/schema.sql)

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

CREATE TABLE doc_sequences (                           -- sinh mã PNK-2026-0001, PXK-, PCK-, PTH-, PDT-
  prefix   VARCHAR(10) NOT NULL,
  year     INT NOT NULL,
  last_no  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, year)
);

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

CREATE TABLE processed_requests (
  idempotency_key VARCHAR(100) PRIMARY KEY,
  response_json   JSON NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
