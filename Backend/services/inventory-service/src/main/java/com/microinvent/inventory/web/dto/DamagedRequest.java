package com.microinvent.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Đánh dấu hàng hư hỏng tại kho (SRS 2.3.6): chuyển từ khả dụng sang qty_damaged; hàng có serial chỉ rõ serial. */
public record DamagedRequest(
        @NotNull(message = "Vui lòng chọn kho") Integer warehouseId,
        @NotNull(message = "Thiếu productId") Integer productId,
        @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
        @Size(max = 200) List<@NotBlank(message = "Serial trống") String> serials) {
}
