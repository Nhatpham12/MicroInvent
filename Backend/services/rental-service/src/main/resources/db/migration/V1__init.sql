-- rental-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE doc_sequences (                           -- sinh mã PNK-2026-0001, PXK-, PCK-, PTH-, PDT-
  prefix   VARCHAR(10) NOT NULL,
  year     INT NOT NULL,
  last_no  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, year)
);

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
