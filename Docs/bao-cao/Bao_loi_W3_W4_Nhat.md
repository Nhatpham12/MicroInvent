# Báo lỗi – kiểm thử tuần 3 và tuần 4 của Nhật (cập nhật sau khi sửa)

> Người kiểm thử: Quân · 20/11/2026 · Người sửa: Nhật · cập nhật 20/11/2026
>
> Phiên bản đã sửa:
> - **W3** → nhánh `fix/nhat-w3-bao-loi` (từ `main` sau PR #8), PR vào `main`.
> - **W4** → thêm commit lên nhánh của PR #7 (`feature/nhat-w4-catalog-notification`).
>
> Bản gốc của Quân: các mục "Cách tái hiện" bên dưới giữ nguyên để kiểm lại; mỗi lỗi có thêm phần **Đã sửa** và **Kết quả kiểm lại**.

## Tóm tắt

| Hạng mục | W3 (`fix/nhat-w3-bao-loi`) | W4 (nhánh PR #7) |
|---|---|---|
| `mvnw clean verify` | BUILD SUCCESS – 115 test | BUILD SUCCESS – 122 test |
| Kiểm thử thật (MariaDB 10.11, RabbitMQ 3.12, gọi thẳng service kèm header gateway) | Đạt hết các ca ở BUG-02..06 | Đạt hết các ca ở BUG-05 (W4), BUG-07, GAP-01, consumer audit |
| Trạng thái | ✅ đã sửa 5/5 lỗi | ✅ đã sửa 2/2 + GAP-01 (phần catalog) |

| Mã | Lỗi | Mức | Tuần | Trạng thái | Sửa ở |
|---|---|---|---|---|---|
| [BUG-02](#bug-02--otp-sai-gửi-song-song-vượt-giới-hạn-5-lần) | OTP sai gửi song song vượt giới hạn 5 lần | 🔴 Cao | W3 | ✅ Đã sửa | `fix/nhat-w3-bao-loi` |
| [BUG-03](#bug-03--reset-token-dùng-được-nhiều-lần-khi-gửi-song-song) | Reset token dùng được nhiều lần khi gửi song song | 🟠 Trung bình | W3 | ✅ Đã sửa | `fix/nhat-w3-bao-loi` |
| [BUG-04](#bug-04--forgot-password-lộ-email-có-tồn-tại-hay-không) | `forgot-password` lộ email có tồn tại hay không | 🟠 Trung bình | W3 | ✅ Đã sửa | `fix/nhat-w3-bao-loi` |
| [BUG-05](#bug-05--tạo-trùng-dữ-liệu-song-song-trả-500-thay-vì-409) | Tạo trùng dữ liệu song song trả 500 thay vì 409 | 🔴 Cao | W3 + W4 | ✅ Đã sửa (common-lib) | `fix/nhat-w3-bao-loi` |
| [BUG-06](#bug-06--gán-kho--đổi-kho-mặc-định-nhận-kho-không-tồn-tại) | Gán kho / đổi kho mặc định nhận kho không tồn tại | 🟢 Thấp | W3 | ✅ Đã sửa | `fix/nhat-w3-bao-loi` (+ 1 API nội bộ ở inventory) |
| [BUG-07](#bug-07--upload-ảnh-song-song-để-lại-file-rác) | Upload ảnh song song để lại file rác | 🟢 Thấp | W4 | ✅ Đã sửa | PR #7 |
| [GAP-01](#gap-01--thông-báo-và-audit-chưa-có-nguồn-dữ-liệu) | Thông báo và audit chưa có nguồn dữ liệu | 🟠 | W4 | 🟡 Một phần – outbox dùng chung + audit của catalog; phần inventory/procurement cần Quân | PR #7 |

> **Thứ tự merge:** merge PR `fix/nhat-w3-bao-loi` vào `main` trước, rồi merge `main` vào nhánh PR #7. BUG-05 phía W4 (NCC, import Excel)
> được sửa bởi `GlobalExceptionHandler` ở common-lib nên chỉ có tác dụng trên nhánh W4 sau bước merge này – kết quả kiểm lại
> W4 bên dưới chạy trên bản gộp 2 nhánh.

---

## BUG-02 · OTP sai gửi song song vượt giới hạn 5 lần · ✅ ĐÃ SỬA

| | |
|---|---|
| **Mức độ** | Cao – giới hạn chống dò OTP không có tác dụng khi gửi dồn dập |
| **Phạm vi** | identity-service – `PasswordService.verifyOtp` |
| **Nguyên nhân** | `attempts` được đọc rồi cộng 1 mà không khóa: nhiều request cùng đọc `attempts=0` → ghi đè lẫn nhau (lost update) |

### Cách tái hiện (Quân)
1. `POST /api/v1/auth/forgot-password` cho 1 tài khoản, lấy OTP trong Mailpit.
2. Gửi **20 request song song** `POST /api/v1/auth/verify-otp` với OTP sai.
3. Gửi OTP đúng.

**Kỳ vọng** – sau 5 lần sai OTP bị hủy, OTP đúng trả 400 `OTP_INVALID`.
**Trước khi sửa** – DB chỉ ghi `attempts = 4` sau 20 lần sai, OTP đúng trả **200** + reset token.

### Đã sửa
- `PasswordOtpRepository.findFirstForUpdateByUserIdOrderByCreatedAtDescIdDesc` có `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`
  trên dòng OTP mới nhất. Các lần verify của cùng tài khoản chạy lần lượt, lần sau đọc được `attempts` lần trước đã ghi.
- Gateway: thêm route `identity-password-recovery` cho `/auth/forgot-password`, `/auth/verify-otp`, `/auth/reset-password` với
  rate limit riêng **10 request/phút/IP** (cộng thêm giới hạn chung). Chưa làm giới hạn theo email vì gateway phải đọc body.

### Kết quả kiểm lại
| Ca | Kết quả |
|---|---|
| 20 × `verify-otp` OTP sai song song | 20 × 400 `OTP_INVALID`; DB `attempts = 5`, `used_at` đã đặt |
| OTP đúng ngay sau đó | **400 `OTP_INVALID`** ✅ |
| Test đơn vị | `PasswordServiceTest.verifyOtpReadsOtpWithRowLock`, `fiveWrongOtpsInvalidateTheCode` |

---

## BUG-03 · Reset token dùng được nhiều lần khi gửi song song · ✅ ĐÃ SỬA

| | |
|---|---|
| **Mức độ** | Trung bình – token "dùng 1 lần" không đảm bảo |
| **Phạm vi** | identity-service – `PasswordService.resetPassword` |
| **Nguyên nhân** | `findByResetTokenHash` không khóa; nhiều request cùng đọc thấy token còn hạn |

### Cách tái hiện (Quân)
Lấy reset token từ `verify-otp`, gửi **6 request song song** `POST /api/v1/auth/reset-password` cùng token.

**Kỳ vọng** – 1 × 200, 5 × 400 `RESET_TOKEN_INVALID`. **Trước khi sửa** – `[200, 200, 200, 200, 200, 400]`.

### Đã sửa
`findForUpdateByResetTokenHash` có `@Lock(PESSIMISTIC_WRITE)`: request đến sau phải chờ request đầu commit; lúc được đọc lại thì
`reset_token_hash` đã là NULL nên không tìm thấy → 400.

### Kết quả kiểm lại
| Ca | Kết quả |
|---|---|
| 6 × `reset-password` song song cùng token | **1 × 200, 5 × 400** ✅ |
| Test đơn vị | `PasswordServiceTest.reusedResetTokenIsRejected` |

---

## BUG-04 · `forgot-password` lộ email có tồn tại hay không · ✅ ĐÃ SỬA

| | |
|---|---|
| **Mức độ** | Trung bình – trái với mô tả "luôn trả OK để không lộ tài khoản" |
| **Phạm vi** | identity-service – `PasswordService.forgotPassword` |

### Cách tái hiện (Quân)
1. Tắt Mailpit. 2. `POST /api/v1/auth/forgot-password` với email có thật và email không có.

**Trước khi sửa** – email có thật 503, email không có 200; khi Mailpit chạy: ~6.200 ms so với ~26 ms.

### Đã sửa
- `forgotPassword` không gửi mail trực tiếp nữa mà phát `OtpMailRequested`; `OtpMailListener` (`@Async` +
  `@TransactionalEventListener(AFTER_COMMIT)`) gửi mail **sau khi commit**, ở luồng khác. SMTP lỗi chỉ ghi log ERROR.
  Transaction rollback thì không gửi mail cho OTP không có trong DB.
- Mọi nhánh (email có thật, không có, bị khóa, gửi lại < 60 giây) đều chạy **đúng 1 lần bcrypt** → thời gian như nhau.
- `OtpMailRequested.toString()` không in OTP ra log.

### Kết quả kiểm lại (SMTP tắt hẳn – cổng không có ai nghe)
| Ca | Kết quả |
|---|---|
| Email có thật / không có | **200 / 200** ✅ |
| Thời gian TB 5 lần (có thật / không có) | **~104 ms / ~90 ms** (trước: 6.200 ms / 26 ms) ✅ |
| Log | `Không gửi được email OTP cho user …` (ERROR, luồng async) – API không bị ảnh hưởng |
| Test đơn vị | `unknownAndKnownEmailBothRunBcryptOnce`, `OtpMailListenerTest.smtpFailureIsOnlyLogged` |

---

## BUG-05 · Tạo trùng dữ liệu song song trả 500 thay vì 409 · ✅ ĐÃ SỬA

| | |
|---|---|
| **Mức độ** | Cao – vi phạm quy tắc "lỗi do client phải trả 4xx", log ERROR giả |
| **Phạm vi** | Mọi chỗ kiểm `existsBy…` rồi mới `save` – identity (user), catalog (SP, NCC, KH, import Excel) |
| **Nguyên nhân** | Bước kiểm không khóa; request sau vướng UNIQUE → `DataIntegrityViolationException` không được bắt → 500 |

### Đã sửa
`GlobalExceptionHandler` (common-lib) bắt `DataIntegrityViolationException` → **409 `CONFLICT`** "Dữ liệu đã tồn tại hoặc bị trùng,
vui lòng tải lại và thử lại"; log WARN (không phải ERROR), không lộ tên bảng/khóa ra response. common-lib thêm `spring-tx`
dạng optional. Mọi service servlet (cả inventory, procurement của Quân) đều được hưởng; chỗ nào đã tự bắt riêng (như
`InternalStockService` của Quân) vẫn giữ nguyên.

### Kết quả kiểm lại
| Ca (song song) | Trước | Sau |
|---|---|---|
| 8 × `POST /api/v1/users` cùng username (W3) | `201, 409×3, 500×4` | **`201, 409×7`** ✅ |
| 8 × `POST /api/v1/products` cùng mã (W3) | `201, 409×3, 500×4` | **`201, 409×7`** ✅ |
| 6 × `POST /api/v1/suppliers` cùng MST (W4, bản gộp) | `201, 409, 500×4` | **`201, 409×5`** ✅ |
| 4 × `POST /api/v1/products/import` cùng mã mới (W4, bản gộp) | `200, 500×3` | **`200, 409×3`** ✅ |

Log: không còn `Unhandled exception`; 8 dòng WARN `Vi phạm ràng buộc dữ liệu: Duplicate entry …`. Test đơn vị:
`GlobalExceptionHandlerTest.duplicateKeyFromConcurrentInsertIs409`.

---

## BUG-06 · Gán kho / đổi kho mặc định nhận kho không tồn tại · ✅ ĐÃ SỬA

| | |
|---|---|
| **Mức độ** | Thấp – dữ liệu rác, FE hiển thị kho không có tên |
| **Phạm vi** | identity-service – `UserService.create`, `assignWarehouses`, `changeDefaultWarehouse` |

### Đã sửa
- **inventory-service** (Quân xem giúp): API nội bộ mới `POST /internal/warehouses/exists` `{ids:[…]}` →
  `{missing:[…], closed:[…]}` (tối đa 500 id, chỉ đọc). Đã thêm vào `Docs/openapi/inventory-service.yaml`.
- **identity-service**: Feign `InventoryClient` + `WarehouseValidator`. Tạo user, gán kho và đổi kho mặc định (với vai trò xem mọi
  kho, chọn kho ngoài danh sách gán) đều kiểm trước: kho không có hoặc đã `CLOSED` → **400** kèm `details.missing / closed`;
  inventory không gọi được → **503** (không gán bừa kho chưa kiểm). Kho `SUSPENDED` vẫn cho gán.

### Kết quả kiểm lại
| Ca | Kết quả |
|---|---|
| `PUT /users/{id}/warehouses` kho 987654 | **400** `details.missing=[987654]` ✅ |
| Gán kho đã CLOSED | **400** `details.closed=[2]` ✅ |
| Gán kho có thật | 200 |
| Admin `PUT /auth/me/default-warehouse` kho 555555 / kho có thật | **400** ✅ / 200 |
| Test đơn vị | `UserServiceTest.assigningUnknownOrClosedWarehouseIsRejected`, `adminDefaultWarehouseMustExist`, `inventoryDownIsServiceUnavailable`; `InternalWarehouseControllerTest` |

---

## BUG-07 · Upload ảnh song song để lại file rác · ✅ ĐÃ SỬA

| | |
|---|---|
| **Phạm vi** | catalog-service – `ProductImageService.upload` |
| **Nguyên nhân** | Các request cùng đọc `imageUrl` cũ; chỉ request commit sau cùng thắng, file của các request khác không bị xóa |

### Đã sửa
Upload đọc sản phẩm bằng `findForUpdateByIdAndDeletedFalse` (`@Lock(PESSIMISTIC_WRITE)`): các upload cho cùng sản phẩm chạy
lần lượt, lần sau thấy `image_url` của lần trước và xóa đúng file đó sau commit.

### Kết quả kiểm lại
| Ca | Trước | Sau |
|---|---|---|
| 5 upload song song cùng sản phẩm | 5 × 200, thư mục thừa 4 file | **5 × 200, còn đúng 1 file, khớp `image_url`** ✅ |

---

## GAP-01 · Thông báo và audit chưa có nguồn dữ liệu · 🟡 MỘT PHẦN

### Đã làm (PR #7)
1. **Outbox dùng chung ở `tenant-starter`** (theo thiết kế nhóm – ghi `outbox_events` trong cùng `@Transactional`, job đẩy đi):
   - `OutboxWriter.append(eventType, aggregateType, aggregateId, payload)` – INSERT vào `outbox_events` của tenant hiện tại;
     gọi ngoài transaction → `IllegalStateException`.
   - `OutboxRelay` – `@Scheduled` mỗi 2 giây, lần lượt từng tenant, `SELECT … FOR UPDATE SKIP LOCKED` 100 dòng `PENDING`,
     gửi bằng `EventPublisher` (giữ nguyên `eventId` → consumer chống trùng), đánh dấu `PUBLISHED`. Gửi lỗi → `retry_count+1`,
     ghi `last_error`, dừng lô; quá 10 lần → `FAILED`.
   - Bật bằng `microinvent.outbox.enabled=true` (mặc định tắt vì service phải có bảng `outbox_events`).
   - Javadoc `AuditPayload` vẫn ghi cách gửi trực tiếp; nên dùng `OutboxWriter` thay thế.
2. **catalog-service** dùng outbox: migration `V2__outbox_events.sql`, `CatalogAudit` ghi `audit.log` cho tạo / sửa / xóa
   SP, nhóm SP, NCC, KH, import Excel, upload ảnh. before/after chỉ có trường nhận diện (mã, tên, MST, SĐT) – **không có STK / CCCD**.
3. **report-service – consumer audit** (góp ý "event lỗi vẫn retry 3 lần"): `AuditRetryConfig` cho retry bỏ qua
   `AmqpRejectAndDontRequeueException`; tenant lạ (`TENANT_NOT_FOUND`) cũng ném loại này → vào DLQ ngay.

### Kết quả kiểm lại
| Ca | Kết quả |
|---|---|
| Tạo rồi xóa SP | `audit_logs` có `CREATE` và `DELETE` `PRODUCT`, đúng `username = admin`, `service = catalog-service`; outbox `PUBLISHED` ✅ |
| Tạo SP trùng mã (409) | Không sinh dòng outbox (rollback cùng thao tác) ✅ |
| Tắt RabbitMQ rồi tạo SP | API vẫn 201; outbox `PENDING`, `retry_count = 1` → bật lại RabbitMQ, trong ~10 s chuyển `PUBLISHED`, `audit_logs` có dòng ✅ |
| 3 event hỏng (2 thiếu `action`, 1 tenant lạ) | Cả 3 vào `report.audit-log.dlq` trong ~20 ms, không chờ retry 1 s + 2 s ✅ |
| Test đơn vị | `OutboxTest` (5), `CatalogAuditTest`, `ProductServiceTest.failedCreateWritesNoAudit`, `SupplierServiceTest` (không lộ STK), `AuditRetryConfigTest` |

### Còn lại – cần thống nhất với Quân
- **Audit phiếu nhập / xuất / chuyển kho**: inventory, procurement, sales, rental đã có bảng `outbox_events` trong V1 → chỉ cần
  `microinvent.outbox.enabled: true` + `spring-boot-starter-amqp` rồi gọi `OutboxWriter.append(EventTypes.AUDIT_LOG, …)` trong
  transaction nghiệp vụ. Event nghiệp vụ (`receipt.confirmed`, `stock.low`…) gửi cùng cách.
- **Cảnh báo `LOW_STOCK`**: đề xuất inventory phát `stock.low` qua outbox, notification-service nghe event và tạo cảnh báo
  (thay vì inventory gọi thẳng `POST /internal/notifications` – gọi đồng bộ dễ mất khi notification tắt).
- **identity-service** chưa ghi audit (đăng nhập, quản lý user) – làm ở W5 cùng bảng outbox cho identity.

---

## Góp ý khác

| Mục | Trạng thái |
|---|---|
| OpenAPI | ✅ `inventory-service.yaml` thêm `/internal/warehouses/exists`; thêm `identity-service.yaml` (nhánh fix) và `catalog-service.yaml`, `notification-service.yaml` (nhánh PR #7) – **sinh tự động** từ springdoc, chưa viết tay mô tả như file của Quân. report-service chưa có API REST nên chưa có file |
| `MAIL_PASSWORD` | ✅ `application.yml` dùng `${MAIL_USERNAME}` / `${MAIL_PASSWORD}` không mặc định; giá trị rỗng chỉ ở `application-dev.yml` (Mailpit). `.env.example` có dòng hướng dẫn |
| `accessStats` | ✅ năm mặc định lấy `LocalDate.now(clock)` trong service (controller không gọi `LocalDate.now()` nữa) |
| Consumer audit | ✅ xem GAP-01 mục 3 |
| Rate limit OTP | ✅ theo IP ở gateway (xem BUG-02); theo email chưa làm |
| Quy trình W3 / W4 (tên nhánh, tự merge) | Ghi nhận: chờ Quân approve trước khi merge. Không đặt được nhánh `nhat/...` vì trên remote đã có nhánh `nhat` (Git không cho `nhat` và `nhat/x` cùng tồn tại) – xóa nhánh `nhat` cũ (đã merge ở PR #6) thì dùng được. Nhánh PR #7 giữ tên cũ để không phải mở lại PR |
| API #28 `?includeStock=true` | ⏳ chờ API tồn của Quân (W4/W5) |
| `PUT /products/{id}` bắt gửi `code` | ⏳ để sau – cần tách DTO tạo / sửa, đụng FE của Nghĩa |
| Kế hoạch T17 (Redis TTL vs bảng `password_otps`) | ⏳ sửa xlsx kế hoạch cho khớp code (bảng `password_otps`) |
| WebSocket vẫn mở khi token hết hạn | ⏳ W5 |
| `CatalogClient` (procurement – Quân) → `/internal/products/batch` | ⏳ sau khi PR #7 merge |

---

## Phần đã xác nhận chạy đúng (Quân, không đổi)

Giữ nguyên như bản gốc – các ca phân quyền, tham số sai → 400, 404 / 409 tuần tự, nghiệp vụ, mật khẩu (W3); NCC / KH, batch nội bộ,
ảnh, Excel, thông báo, WebSocket, audit (W4). Đã chạy lại toàn bộ test đơn vị sau khi sửa, không có ca nào hỏng.

## Cách kiểm lại nhanh

```bash
# W3 – trên nhánh fix/nhat-w3-bao-loi
cd Backend && mvnw clean verify
# 20 OTP sai song song (như kịch bản của Quân), rồi OTP đúng → phải 400
# 8 POST /api/v1/users cùng username song song → 201 + 409×7, không có 500
# W4 – sau khi merge main (đã có fix/nhat-w3-bao-loi) vào nhánh PR #7
# 5 upload ảnh song song → còn 1 file; tạo/xóa SP → xem report_<tenant>.audit_logs
```
