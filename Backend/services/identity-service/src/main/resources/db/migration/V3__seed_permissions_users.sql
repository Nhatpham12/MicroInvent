-- identity-service – quyền mặc định theo vai trò + tài khoản demo
-- Mật khẩu demo: admin / Admin@123, thukho / Thukho@123, banhang / Thukho@123 (đổi ngay khi triển khai thật).

INSERT INTO permissions (code, description) VALUES
 ('product.view','Xem sản phẩm, nhóm sản phẩm'),
 ('product.manage','Thêm/sửa/xóa sản phẩm, nhóm sản phẩm'),
 ('partner.manage','Quản lý nhà cung cấp, khách hàng'),
 ('warehouse.manage','Quản lý kho'),
 ('receipt.create','Lập phiếu nhập'),
 ('receipt.confirm','Xác nhận phiếu nhập'),
 ('export.create','Lập phiếu xuất/bán'),
 ('export.confirm','Xác nhận xuất kho'),
 ('transfer.create','Lập phiếu chuyển kho'),
 ('transfer.approve','Duyệt phiếu chuyển kho'),
 ('stocktake.manage','Kiểm kê'),
 ('rental.manage','Cho thuê/cho mượn'),
 ('payment.manage','Thanh toán, công nợ'),
 ('report.view','Xem báo cáo, dashboard'),
 ('user.manage','Quản lý người dùng, phân quyền'),
 ('setting.manage','Cấu hình hệ thống');

-- ADMIN: tất cả
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

-- MANAGER: tất cả trừ quản lý người dùng và cấu hình
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'MANAGER' AND p.code NOT IN ('user.manage','setting.manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('product.view','partner.manage','payment.manage','report.view')
WHERE r.code = 'ACCOUNTANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('product.view','product.manage','receipt.create','receipt.confirm','export.confirm',
                'transfer.create','stocktake.manage')
WHERE r.code = 'STOREKEEPER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('product.view','partner.manage','export.create','rental.manage')
WHERE r.code = 'SALES';

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
