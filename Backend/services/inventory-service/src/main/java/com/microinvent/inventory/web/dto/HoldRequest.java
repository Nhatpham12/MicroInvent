package com.microinvent.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Giữ hàng thủ công cho khách tại 1 kho (SRS 3.4.2). Sản phẩm có serial thì chỉ rõ serial (số serial = qty).
 *
 * @param expiresAt bỏ trống = sau {@code microinvent.hold.default-days} ngày (mặc định 3, khớp HOLD_DAYS)
 */
public record HoldRequest(
        @NotNull(message = "Vui lòng chọn kho") Integer warehouseId,
        @NotNull(message = "Thiếu productId") Integer productId,
        @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
        @Size(max = 200) List<@NotBlank(message = "Serial trống") String> serials,
        Integer customerId,
        @Size(max = 255) String customerName,
        LocalDateTime expiresAt) {
}
