package com.microinvent.sales.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Kiểm tồn khả dụng tại kho xuất trước khi lập phiếu (UC-002 4a). */
public record AvailabilityRequest(
        @NotNull(message = "Vui lòng chọn kho xuất") Integer warehouseId,
        @NotEmpty(message = "Danh sách trống") @Size(max = 200) List<@Valid Item> items) {

    public record Item(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty) {
    }
}
