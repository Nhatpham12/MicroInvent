-- report-service – schema khởi tạo (tách từ Docs/database/schema.sql)

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
