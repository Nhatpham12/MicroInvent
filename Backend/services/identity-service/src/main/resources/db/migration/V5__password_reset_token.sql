-- Quên mật khẩu (T17): sau khi xác thực OTP, cấp reset token dùng 1 lần, chỉ lưu SHA-256.
-- (V4 là dữ liệu demo trong db/dev-seed, chỉ nạp ở profile dev.)
ALTER TABLE password_otps
  ADD COLUMN reset_token_hash  VARCHAR(64) NULL UNIQUE,
  ADD COLUMN reset_expires_at  DATETIME    NULL,
  ADD INDEX idx_otp_user_time (user_id, created_at);
