-- Transactional outbox (GAP-01): event audit.log ghi cùng transaction với thao tác, OutboxRelay đẩy lên RabbitMQ.
-- Cùng cấu trúc với outbox_events của inventory / procurement / sales / rental (Docs/database/schema.sql).
CREATE TABLE outbox_events (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id       CHAR(36) NOT NULL UNIQUE,
  event_type     VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(30) NOT NULL,
  aggregate_id   INT NOT NULL,
  payload        JSON NOT NULL,
  status         ENUM('PENDING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  retry_count    INT NOT NULL DEFAULT 0,
  last_error     TEXT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at   DATETIME NULL,
  INDEX idx_outbox_status (status, created_at)
);
