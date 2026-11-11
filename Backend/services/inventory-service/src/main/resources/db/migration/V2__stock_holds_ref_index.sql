-- inventory-service – index tra hold theo chứng từ (reserve / commit / release của API nội bộ).
-- Thiếu index này, SELECT … FOR UPDATE theo (ref_type, ref_id) quét và khóa cả bảng stock_holds.
CREATE INDEX idx_holds_ref ON stock_holds (ref_type, ref_id);
