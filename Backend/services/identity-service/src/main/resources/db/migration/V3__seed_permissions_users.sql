-- identity-service – quyền mặc định theo vai trò.
-- Tài khoản demo KHÔNG nằm ở đây: xem db/dev-seed/V4__seed_demo_users.sql (chỉ nạp khi chạy profile dev).

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
