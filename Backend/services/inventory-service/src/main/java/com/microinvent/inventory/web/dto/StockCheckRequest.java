package com.microinvent.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Kiểm tồn khả dụng nhiều dòng tại 1 kho (UC-002 4a). */
public record StockCheckRequest(@NotNull(message = "Thiếu warehouseId") Integer warehouseId,
        @NotEmpty(message = "Danh sách trống") List<@Valid Item> items) {

    public record Item(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu qty") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty) {
    }
}
