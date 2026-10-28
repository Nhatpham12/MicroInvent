-- sales-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE doc_sequences (                           -- sinh mã PNK-2026-0001, PXK-, PCK-, PTH-, PDT-
  prefix   VARCHAR(10) NOT NULL,
  year     INT NOT NULL,
  last_no  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, year)
);

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
