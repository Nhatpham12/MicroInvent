-- notification-service – schema khởi tạo (tách từ Docs/database/schema.sql)

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

CREATE TABLE processed_events (                        -- consumer idempotency
  event_id     VARCHAR(100) PRIMARY KEY,
  processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
