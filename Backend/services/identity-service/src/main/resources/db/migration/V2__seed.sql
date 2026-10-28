-- identity-service – dữ liệu mặc định

INSERT INTO roles (code, name, all_warehouses) VALUES
 ('ADMIN','Quản trị hệ thống',TRUE),('MANAGER','Nhà quản lý',TRUE),
 ('ACCOUNTANT','Kế toán',TRUE),('STOREKEEPER','Thủ kho',FALSE),('SALES','Nhân viên bán hàng',FALSE);
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('HOLD_DAYS','3','Số ngày giữ hàng mặc định'),
 ('TRANSFER_OVERDUE_DAYS','3','Số ngày chuyển kho trước khi cảnh báo quá hạn'),
 ('TRANSFER_APPROVAL_THRESHOLD','0','Giá trị phiếu chuyển dưới ngưỡng này được bỏ qua duyệt (0 = luôn duyệt)'),
 ('RENT_DUE_WARNING_DAYS','7','Cảnh báo trước khi hết hạn thuê/mượn'),
 ('RENT_PENALTY_FROM_DAYS','10','Tính phạt khi quá hạn từ N ngày'),
 ('DEBT_OVERDUE_DAYS','30','Công nợ quá hạn'),
 ('MAX_FAILED_LOGIN','5','Số lần đăng nhập sai trước khi khóa'),
 ('LOCK_MINUTES','5','Thời gian khóa tạm thời');
