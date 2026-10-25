# Đồ án Inventory Microservice (tên tạm) – Thiết kế hệ thống

> Nguồn: SRS TigerNet v1.1 (Google Drive, cập nhật 10/2026). Bản này viết lại cho stack **NodeJS Express (microservice) + React Vite**, nhóm 3 người (Nhật, Quân: backend · Nghĩa: frontend + Figma), thời gian 2 tháng (26/10/2026 → 25/12/2026).
> Kèm theo: `database/schema.sql`, `diagrams/*.png|svg` (nguồn Mermaid trong `diagrams/mermaid-src/`), `Ke_hoach_Do_an_Inventory_Microservice.xlsx`.

## 1. Phạm vi đồ án (MVP 2 tháng)

**Làm (theo SRS):** đăng nhập/JWT/OTP quên mật khẩu, khóa tài khoản 5 lần sai; người dùng & phân quyền theo vai trò + theo kho; danh mục sản phẩm/nhóm/NCC/khách hàng; danh mục kho đa địa điểm; tồn kho theo (Sản phẩm, Kho) và theo serial; nhập kho; xuất kho + gợi ý kho khác khi thiếu + tách phiếu; giữ hàng (hold) có hạn; cho thuê/cho mượn + trả ở kho khác + tính phạt; đổi/trả theo bảng phí 3.8; chuyển kho đủ 8 trạng thái + sai lệch; kiểm kê + điều chỉnh; thanh toán/công nợ; cảnh báo realtime; báo cáo (tồn, nhập/xuất, doanh thu theo SP/KH/NV/kho, cho thuê, công nợ, đa kho) + xuất Excel/PDF; audit log; backup tự động (mysqldump cron).

**Không làm / để "stretch":** app Flutter mobile (thay bằng web responsive), offline mode, vị trí lưu trữ Khu-Kệ-Ô (Phase 2 – làm nếu dư thời gian), đăng nhập Google OAuth, xuất .doc/.xml.

## 2. Thay đổi so với SRS gốc

| SRS gốc | Thiết kế mới | Lý do |
|---|---|---|
| Java Spring Boot + Flutter | NodeJS Express + React Vite | Yêu cầu đồ án |
| 1 database MySQL tập trung | 5 database (database-per-service) trên cùng 1 MySQL server | Đúng mô hình microservice, vẫn dễ chạy bằng docker-compose |
| Bảng `Inventory` gộp cả tồn kho + bán + thanh toán | Tách: `stock_balances`, `stock_items`, `stock_movements` (inventory) và chứng từ `goods_receipts`, `export_slips`, `rental_contracts`… (order) | Một dòng hàng không thể vừa là tồn vừa là hóa đơn; chuẩn hóa để báo cáo đúng |
| Bảng tra cứu `Transaction_Types`, `VAT_Options`, `Payment_Statuses` | ENUM | Ít thay đổi, bớt join chéo service |
| FK giữa mọi bảng | FK chỉ trong 1 service; chéo service lưu ID + snapshot JSON (tên, MST…) | Service độc lập, chứng từ in ra không đổi khi danh mục đổi |
| `Hold_By_User_ID`, `Hold_Until` trên Inventory | Bảng `stock_holds` | Giữ được hàng không serial theo số lượng, có lịch sử |
| Chưa có kiểm kê, đổi trả, thanh toán dạng bảng | `stocktakes`, `sales_returns`, `rental_returns`, `payments`, `transfer_discrepancies` | SRS mô tả nghiệp vụ nhưng thiếu bảng |
| — | `outbox_events`, `processed_events`, `processed_requests` | Gửi event tin cậy & chống xử lý trùng |
| View `View_Stock_By_Warehouse`… | Endpoint + read model ở report-service (`fact_sales`, `stock_snapshots`) | Không join chéo database được |

## 3. Kiến trúc

![Kiến trúc](diagrams/01-kien-truc-he-thong.png)

| Service | Port | Database | Trách nhiệm |
|---|---|---|---|
| api-gateway | 8080 | – | Định tuyến `/api/v1/*`, xác thực JWT, rate limit theo user/ngày, CORS, gộp Swagger, proxy WebSocket |
| auth-service | 4001 | auth_db | Đăng nhập, refresh token, OTP, user, role/permission, gán kho, cấu hình hệ thống |
| catalog-service | 4002 | catalog_db | Sản phẩm, nhóm SP, nhà cung cấp, khách hàng, upload ảnh |
| inventory-service | 4003 | inventory_db | Kho, tồn kho, serial, giữ hàng, nhật ký biến động, kiểm kê. **Nguồn sự thật duy nhất về tồn** |
| order-service | 4004 | order_db | Phiếu nhập, xuất, thuê/mượn, trả, đổi trả, chuyển kho, thanh toán, công nợ, bảo hành |
| report-service | 4005 | report_db | Báo cáo, dashboard, cảnh báo (cron + event), Socket.IO, audit log, xuất Excel/PDF, backup |

**Giao tiếp**
- *Đồng bộ (REST nội bộ, header `X-Internal-Key` + `Idempotency-Key`)*: order → inventory (`check`, `reserve`, `commit`, `release`, `receive`, `transfer-out`, `transfer-in`), order → catalog (lấy SP/KH theo danh sách ID).
- *Bất đồng bộ (RabbitMQ, topic exchange `inventory.events`)*: `receipt.confirmed`, `export.exported`, `rental.created|returned`, `return.completed`, `transfer.submitted|shipped|received|discrepancy`, `stock.changed`, `stock.low`, `hold.expired`, `audit.log` → report-service tiêu thụ.
- *Nhất quán tồn kho (SRS 3.10.7)*: mọi thay đổi tồn trong inventory-service chạy trong 1 transaction MySQL với `SELECT … FOR UPDATE` trên `stock_balances` + kiểm `version`; Redis lock (`lock:stock:{productId}:{warehouseId}`) cho thao tác serial đồng thời → test case 8.5#5 chỉ 1 giao dịch thành công.
- *Phân quyền theo kho (SRS 3.10.3, 8.3)*: gateway giải mã JWT (`userId`, `role`, `warehouseIds`, `allWarehouses`) và chuyển xuống service qua header; middleware `requireWarehouseAccess(write)` trong `packages/common` kiểm tra kho của request ở **server**.

Luồng tạo & xác nhận phiếu xuất:

![Luồng xuất kho](diagrams/08-luong-xuat-kho.png)

## 4. Database (viết lại)

DDL đầy đủ: [`database/schema.sql`](database/schema.sql) – MySQL 8, 5 database, 47 bảng, kèm seed vai trò và cấu hình.

| Database | Bảng | ERD |
|---|---|---|
| auth_db | roles, permissions, role_permissions, users, user_warehouses, refresh_tokens, password_otps, login_histories, system_settings | `diagrams/02-erd-auth-service.png` |
| catalog_db | product_groups, products, suppliers, customers | `diagrams/03-erd-catalog-service.png` |
| inventory_db | warehouses, warehouse_locations, product_warehouse_settings, stock_balances, stock_items, stock_holds, stock_movements, stocktakes, stocktake_items, processed_requests | `diagrams/04-erd-inventory-service.png` |
| order_db | doc_sequences, goods_receipts(+items), export_slips(+items), rental_contracts, rental_items, rental_returns(+items), sales_returns(+items), stock_transfers(+items), transfer_discrepancies, payments, outbox_events | `diagrams/05-erd-order-service.png` |
| report_db | notifications, audit_logs, fact_sales, fact_stock_movements, stock_snapshots, export_jobs, backup_logs, processed_events | `diagrams/06-erd-report-service.png` |

Công thức tồn (SRS 3.10.4): `available = qty_on_hand − qty_hold − qty_damaged`; tổng hệ thống = Σ `qty_on_hand` + Σ hàng `IN_TRANSIT`.

## 5. Cấu trúc dự án (monorepo, npm workspaces)

```
inventory-ms/
├── .github/
│   ├── workflows/ci.yml            # lint + test + build theo từng service (paths filter)
│   ├── pull_request_template.md
│   └── CODEOWNERS                  # services/inventory-service/ @quan ...
├── docs/                           # SRS, ERD, OpenAPI tổng, link Figma, biên bản họp
├── infra/
│   ├── mysql/init/01-schema.sql    # = database/schema.sql
│   ├── rabbitmq/definitions.json   # exchange, queue, binding
│   └── nginx/default.conf          # serve FE build + proxy /api
├── packages/
│   └── common/                     # thư viện dùng chung
│       └── src/ logger.js · AppError.js · errorHandler.js · response.js · validate.js (zod)
│               auth-context.js · requireRole.js · requireWarehouseAccess.js
│               rabbitmq.js (publish/subscribe) · outbox.js · idempotency.js · httpClient.js
├── services/
│   ├── api-gateway/
│   │   └── src/ server.js · config/ · middlewares/(auth, rateLimit, cors) · routes/proxy.js · swagger.js
│   ├── auth-service/
│   │   ├── prisma/ schema.prisma · migrations/ · seed.js
│   │   ├── src/
│   │   │   ├── app.js · server.js
│   │   │   ├── config/ (env, db, redis, mailer)
│   │   │   ├── modules/
│   │   │   │   ├── auth/      auth.routes.js · auth.controller.js · auth.service.js · auth.schema.js
│   │   │   │   ├── users/     users.routes.js · users.controller.js · users.service.js · users.schema.js
│   │   │   │   └── settings/
│   │   │   ├── events/ (publishers/, consumers/)
│   │   │   └── docs/openapi.yaml
│   │   ├── tests/ (unit/, integration/)
│   │   ├── Dockerfile · .env.example · package.json
│   ├── catalog-service/            # modules: product-groups, products, suppliers, customers, uploads
│   ├── inventory-service/          # modules: warehouses, locations, stock, holds, movements, stocktakes, internal
│   │   └── src/jobs/ holdExpiry.job.js
│   ├── order-service/              # modules: receipts, exports, rentals, returns, transfers, payments, debts, warranty
│   │   └── src/jobs/ outboxPublisher.job.js
│   └── report-service/             # modules: dashboard, reports, exports, notifications, audit, backups
│       └── src/ jobs/(alerts.job.js, snapshot.job.js, backup.job.js) · socket/ · consumers/
├── frontend/                       # React 18 + Vite
│   ├── src/
│   │   ├── main.jsx · App.jsx
│   │   ├── app/ router.jsx · queryClient.js · store/ (authStore, warehouseStore, themeStore)
│   │   ├── api/ axiosClient.js (interceptor refresh token, X-Warehouse-Id) · socket.js
│   │   ├── components/ ui/ (DataTable, FormModal, StatusTag, MoneyInput, SerialScanner...)
│   │   │               layout/ (AppHeader, Sidebar, WarehouseSelector, NotificationBell)
│   │   ├── features/   auth · dashboard · products · partners · warehouses · receipts · exports
│   │   │               holds · transfers · rentals · returns · stocktakes · payments · reports
│   │   │               notifications · users · settings · audit
│   │   │               (mỗi feature: pages/ · components/ · hooks/ · <feature>.api.js)
│   │   ├── layouts/ MainLayout.jsx · AuthLayout.jsx
│   │   ├── routes/ ProtectedRoute.jsx · RoleGuard.jsx
│   │   ├── styles/ tokens.css (lấy từ Figma) · theme.js (light/dark)
│   │   ├── utils/ format.js (tiền VND, ngày) · constants.js (enum trạng thái)
│   │   └── locales/ vi.json
│   ├── vite.config.js · .env.example (VITE_API_URL) · Dockerfile
├── docker-compose.yml              # mysql, redis, rabbitmq, mailhog, 6 service, frontend
├── docker-compose.dev.yml          # hot-reload (nodemon / vite)
├── .env.example · .eslintrc · .prettierrc · .editorconfig
└── package.json                    # workspaces: packages/*, services/*, frontend
```

**Thư viện đề xuất**
- Backend: Express 4, Prisma (MySQL), Zod, jsonwebtoken, bcrypt, ioredis, amqplib, node-cron, nodemailer (MailHog khi dev), multer, ExcelJS, PDFKit, Socket.IO, swagger-ui-express, pino, Jest + Supertest, ESLint + Prettier.
- Frontend: React 18, Vite, React Router 6, Ant Design 5 (bảng, form, locale vi), TanStack Query, Zustand, Axios, React Hook Form + Zod, Recharts, dayjs, socket.io-client, Vitest + Testing Library.

**Quy ước API:** prefix `/api/v1`, JSON `{ success, data, meta, error: { code, message, details } }`, phân trang `?page=&limit=&sort=field:asc`, header `X-Warehouse-Id` cho kho đang làm việc, mã lỗi nghiệp vụ dạng `STOCK_NOT_ENOUGH`, `WAREHOUSE_FORBIDDEN`. Danh sách đầy đủ 157 endpoint ở các sheet **API - Nhật**, **API - Quân**, **API - Nghĩa (FE)** trong file xlsx.

## 6. Git workflow

![Git workflow](diagrams/07-git-workflow.png)

- **Nhánh:** `main` (bản demo ổn định, gắn tag cuối mỗi sprint) · `develop` (tích hợp) · `feature/<scope>-<mô-tả>` · `fix/<scope>-<mô-tả>` · `release/vX.Y` · `hotfix/<mô-tả>`. `scope` = `auth|catalog|inventory|order|report|gateway|fe|infra|docs`.
- **Commit:** Conventional Commits – `feat(inventory): giữ hàng có thời hạn`, `fix(order): …`, `test`, `docs`, `chore`, `refactor`. Kiểm bằng commitlint + husky.
- **Pull Request:** vào `develop`, nhỏ (< 400 dòng), có mô tả + link issue + ảnh màn hình (FE). CI xanh + ≥ 1 approve (Nhật ↔ Quân review chéo; PR frontend do Nhật hoặc Quân review phần gọi API; PR đổi API contract phải có Nghĩa approve). **Squash merge**, xóa nhánh sau merge.
- **Bảo vệ nhánh:** cấm push thẳng `main`/`develop`; bắt buộc CI + review.
- **Release:** cuối mỗi sprint (2 tuần) tạo `release/v0.N` → test → merge `main` + tag `v0.N.0` → merge ngược `develop`. Hotfix từ `main`, merge vào cả `main` và `develop`.
- **Quản lý việc:** GitHub Projects (Backlog → To do → In progress → Review → Done); mỗi task trong sheet "Chia việc" là 1 issue, đặt label service + sprint.

## 7. Định nghĩa hoàn thành (DoD)

Code đã review & merge vào `develop`; có test (service ≥ 60% coverage cho logic tồn kho/chứng từ); OpenAPI cập nhật; chạy được bằng `docker compose up`; màn hình FE khớp Figma và responsive; kịch bản 8.5 của SRS liên quan đã pass.
