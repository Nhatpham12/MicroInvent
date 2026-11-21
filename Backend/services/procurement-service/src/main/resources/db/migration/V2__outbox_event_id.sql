-- procurement-service – event_id cố định cho mỗi dòng outbox: gửi lại (retry) vẫn cùng id,
-- consumer chống xử lý trùng theo processed_events.event_id.
ALTER TABLE outbox_events
  ADD COLUMN event_id   CHAR(36) NULL AFTER id,
  ADD COLUMN last_error TEXT     NULL;
UPDATE outbox_events SET event_id = UUID() WHERE event_id IS NULL;
ALTER TABLE outbox_events
  MODIFY event_id CHAR(36) NOT NULL,
  ADD UNIQUE KEY uq_outbox_event_id (event_id);
