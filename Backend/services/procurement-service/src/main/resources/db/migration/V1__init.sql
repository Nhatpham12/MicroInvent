-- procurement-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE doc_sequences (                           -- sinh mã PNK-2026-0001, PXK-, PCK-, PTH-, PDT-
  prefix   VARCHAR(10) NOT NULL,
  year     INT NOT NULL,
  last_no  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, year)
);

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
