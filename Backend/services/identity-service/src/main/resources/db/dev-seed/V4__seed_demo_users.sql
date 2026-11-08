-- CHỈ NẠP KHI CHẠY PROFILE dev (application-dev.yml thêm classpath:db/dev-seed vào Flyway). Không có ở môi trường thật.
-- Mật khẩu demo: admin / Admin@123, thukho / Thukho@123, banhang / Thukho@123.

-- Tài khoản demo. warehouse_id = 1 là kho đầu tiên tạo ở inventory-service.
INSERT INTO users (username, email, password_hash, full_name, role_id, default_warehouse_id)
SELECT 'admin', 'admin@tigernet.vn', '$2a$10$8eRCVXKGe.E5.bDjaGZSN.WrDxHbO/3z.IxYdT1jxy4n.8z/pBhQm',
       'Quản trị hệ thống', id, NULL FROM roles WHERE code = 'ADMIN';
INSERT INTO users (username, email, password_hash, full_name, role_id, default_warehouse_id)
SELECT 'thukho', 'thukho@tigernet.vn', '$2a$10$AQKyfnFcwhNApPK1Jv7gvuR046v9H8dd1ww7w6NEcA1jGKtLNuTPC',
       'Thủ kho demo', id, 1 FROM roles WHERE code = 'STOREKEEPER';
INSERT INTO users (username, email, password_hash, full_name, role_id, default_warehouse_id)
SELECT 'banhang', 'banhang@tigernet.vn', '$2a$10$AQKyfnFcwhNApPK1Jv7gvuR046v9H8dd1ww7w6NEcA1jGKtLNuTPC',
       'Nhân viên bán hàng demo', id, 1 FROM roles WHERE code = 'SALES';

INSERT INTO user_warehouses (user_id, warehouse_id, can_write)
SELECT id, 1, TRUE FROM users WHERE username IN ('thukho','banhang');
