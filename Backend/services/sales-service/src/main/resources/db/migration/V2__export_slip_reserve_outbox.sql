-- sales-service – phiếu xuất (T31)
-- stock_reserved: phiếu đã giữ đủ hàng ở kho xuất chưa. Phiếu thiếu hàng vẫn lưu PENDING (chưa giữ) để tách
-- theo kho (/exports/{id}/split); chỉ xác nhận được khi đã giữ đủ.
ALTER TABLE export_slips
  ADD COLUMN stock_reserved BOOLEAN NOT NULL DEFAULT FALSE AFTER status;

-- outbox_events theo cấu trúc OutboxWriter / OutboxRelay của tenant-starter (event_id cố định để consumer chống trùng).
ALTER TABLE outbox_events
  ADD COLUMN event_id       CHAR(36)    NULL AFTER id,
  ADD COLUMN aggregate_type VARCHAR(30) NOT NULL DEFAULT 'EXPORT_SLIP' AFTER event_type,
  ADD COLUMN last_error     TEXT        NULL;
UPDATE outbox_events SET event_id = UUID() WHERE event_id IS NULL;
ALTER TABLE outbox_events
  MODIFY event_id CHAR(36) NOT NULL,
  ADD UNIQUE KEY uq_outbox_event_id (event_id);
