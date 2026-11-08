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
| api-gateway | 8080 | – | Route `/api/v1/**` → `lb://<service>`, xác thực JWT, rate limit (Redis), CORS, gộp Swagger |
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

## Xác thực & phân quyền

```
FE ──Bearer JWT──▶ api-gateway ──X-User-*, X-Tenant-Id, X-Trace-Id──▶ service
                   (JwtAuthenticationFilter)          (ContextHeaderFilter → UserContextHolder)
```

- `POST /api/v1/auth/login` (identity-service) trả `accessToken` (JWT, 15 phút) + `refreshToken` (7 ngày, xoay vòng
  mỗi lần `POST /api/v1/auth/refresh`). Sai mật khẩu 5 lần → khóa 5 phút (`MAX_FAILED_LOGIN`, `LOCK_MINUTES` trong
  `system_settings`). Mọi lần đăng nhập ghi `login_histories`.
- Gateway xóa mọi header `X-User-*`/`X-Internal-Key` client tự gửi, xác thực token rồi gắn lại từ claims; tenant lấy
  từ token (ghi đè `X-Tenant-Id`; token không có tenant thì header này bị xóa, riêng đường dẫn public như login vẫn nhận
  `X-Tenant-Id` để chọn tenant). Đường dẫn không cần token: `microinvent.gateway.public-paths`.
- Trong service: `UserContextHolder.get()` để biết ai đang gọi; `@RequireRoles({"ADMIN","MANAGER"})` để chặn theo vai trò
  (đặt trên class = chỉ cần đăng nhập, trên method thì ghi đè); `@RequireWarehouseAccess` để kiểm quyền trên kho
  `X-Warehouse-Id`. Mẫu: `catalog-service` → `ProductGroupController`.
- `/internal/**` chỉ cho service gọi nhau: Feign tự gắn `X-Internal-Key` (+ tenant, user, traceId) nhờ
  `InternalFeignInterceptor`; gateway không route `/internal/**` ra ngoài.
- Rate limit: 20 request/giây, burst 40 theo user (chưa đăng nhập thì theo IP) – `RATE_LIMIT_PER_SECOND`, `RATE_LIMIT_BURST`.

Khóa bí mật: `JWT_SECRET` (>= 32 byte, gateway và identity-service dùng chung) và `INTERNAL_API_KEY` **không có giá trị mặc
định**; thiếu thì service không khởi động (thiếu `INTERNAL_API_KEY` ở service không khai báo thì mọi `/internal/**` bị từ chối).
Sinh khóa: `openssl rand -base64 48`. Máy dev bật profile `dev` (`SPRING_PROFILES_ACTIVE=dev`, đã có sẵn trong `.env.example`;
chạy `spring-boot:run` thì thêm `-Dspring-boot.run.profiles=dev`) để dùng khóa dev trong `application-dev.yml`.

Tài khoản demo chỉ có khi chạy profile `dev` (migration `db/dev-seed/V4__seed_demo_users.sql`, tenant `tigernet`). Môi trường
thật không có tài khoản nào, phải tạo admin đầu tiên riêng. DB dev đã chạy bản V3 cũ (có user) thì tạo lại DB hoặc `flyway repair`:

| Username | Mật khẩu | Vai trò | Kho |
|---|---|---|---|
| admin | Admin@123 | ADMIN | tất cả |
| thukho | Thukho@123 | STOREKEEPER | kho id 1 |
| banhang | Thukho@123 | SALES | kho id 1 |

Swagger gộp tại gateway: http://localhost:8080/swagger-ui.html (chọn service ở góc trên, bấm Authorize dán access token).

## common-lib dùng chung

| Thành phần | Tự bật khi | Dùng |
|---|---|---|
| `ApiResponse`, `PageResponse`, `BusinessException` + `ErrorCode`, `GlobalExceptionHandler` | service servlet | Khuôn `{ success, data, meta, error }` |
| `ContextHeaderFilter`, `UserContextHolder`, `TenantContext` | service servlet | Ai đang gọi, tenant nào, traceId trong MDC |
| `GET /health` | service servlet | Health check nhẹ |
| `InternalApiKeyFilter` | service servlet | Bảo vệ `/internal/**` |
| `@RequireRoles`, `@RequireWarehouseAccess` | có `spring-boot-starter-aop` | Kiểm vai trò / kho |
| `JwtService` | có jjwt + `microinvent.jwt.secret` | identity ký, gateway xác thực |
| `EventPublisher`, JSON converter, exchange `inventory.events` | có `spring-boot-starter-amqp` | `eventPublisher.publish(EventTypes.RECEIPT_CONFIRMED, id, payload)` |
| `InternalFeignInterceptor` | có OpenFeign | Gắn header nội bộ cho mọi FeignClient |
| `IdempotencyService` | có `spring-boot-starter-data-redis` | `idempotency.execute("receipt.confirm", key, Dto.class, () -> ...)` |
| `OpenAPI` (nút Authorize Bearer) | có springdoc | Swagger mỗi service |

## Chạy local

```bat
copy .env.example .env
docker compose up -d mysql redis rabbitmq mailpit
mvnw.cmd install -DskipTests
mvnw.cmd -pl platform/discovery-server spring-boot:run
mvnw.cmd -pl services/catalog-service spring-boot:run
```

Hoặc chạy cả hệ thống: `docker compose up -d --build`.

Build & test: `mvnw.cmd clean verify` (test context của các service DB sẽ thêm bằng Testcontainers).
Nên luôn có `clean` khi đóng gói jar để chạy: build tăng dần đôi khi giữ lại bản common-lib cũ bên trong jar Spring Boot.
