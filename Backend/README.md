# MicroInvent – Backend

Spring Boot 3.5.16 · Spring Cloud 2025.0.3 · Java 21 · Maven multi-module · MySQL 8 · RabbitMQ · Redis.

## Cấu trúc

```
Backend/
├── pom.xml · docker-compose.yml · Dockerfile · .env.example · README.md
├── docker/mysql/init.sql
├── libs/
│   ├── common-lib       context · security · exception · web · event · autoconfigure
│   └── tenant-starter   routing DataSource + Flyway theo tenant
├── platform/
│   ├── discovery-server :8761
│   └── api-gateway      :8080
└── services/
    ├── tenant-service       :8090
    ├── identity-service     :8081
    ├── catalog-service      :8082
    ├── inventory-service    :8083
    ├── procurement-service  :8084
    ├── sales-service        :8085
    ├── rental-service       :8086
    ├── report-service       :8087
    ├── notification-service :8088
    └── file-service         :8089
```

| Module | Port | Database | Trách nhiệm |
|---|---|---|---|
| discovery-server | 8761 | – | Eureka |
| api-gateway | 8080 | – | Route `/api/v1/**` → `lb://<service>`, CORS, (JWT – TODO) |
| tenant-service | 8090 | tenant_db | Quản lý tenant và database của từng tenant |
| identity-service | 8081 | identity_<tenant> | Đăng nhập, JWT, OTP, người dùng, vai trò, phân quyền theo kho |
| catalog-service | 8082 | catalog_<tenant> | Danh mục sản phẩm, nhóm sản phẩm, nhà cung cấp, khách hàng |
| inventory-service | 8083 | inventory_<tenant> | Kho, tồn kho, serial, giữ hàng, chuyển kho, kiểm kê – nguồn sự thật về tồn |
| procurement-service | 8084 | procurement_<tenant> | Phiếu nhập kho (mua hàng từ nhà cung cấp) |
| sales-service | 8085 | sales_<tenant> | Phiếu xuất/bán, đổi trả, thanh toán, công nợ |
| rental-service | 8086 | rental_<tenant> | Hợp đồng cho thuê/cho mượn, trả hàng, tính phạt |
| report-service | 8087 | report_<tenant> | Báo cáo, dashboard, audit log, xuất Excel/PDF, backup |
| notification-service | 8088 | notification_<tenant> | Cảnh báo realtime (WebSocket STOMP) và email |
| file-service | 8089 | file_<tenant> | Upload/tải file (ảnh sản phẩm, chứng từ đính kèm) |

## Đa tenant

- Client gửi header `X-Tenant-Id` (không có thì dùng `DEFAULT_TENANT`, mặc định `tigernet`).
- `common-lib` đọc header vào `TenantContext`; `tenant-starter` tạo một DataSource cho mỗi tenant
  (`microinvent.tenant.url-template`, `%s` = mã tenant), chạy Flyway `db/migration` trên từng database
  và định tuyến bằng `TenantRoutingDataSource`.
- Thêm tenant: khai báo trong `TENANTS=tigernet,abc` (sau này lấy từ tenant-service).

## Chạy local

```bat
copy .env.example .env
docker compose up -d mysql redis rabbitmq mailpit
mvnw.cmd install -DskipTests
mvnw.cmd -pl platform/discovery-server spring-boot:run
mvnw.cmd -pl services/catalog-service spring-boot:run
```

Hoặc chạy cả hệ thống: `docker compose up -d --build`.

Build & test: `mvnw.cmd verify` (test context của các service DB sẽ thêm bằng Testcontainers).
