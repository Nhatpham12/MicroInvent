package com.microinvent.identity.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Thay toàn bộ danh sách kho được gán (gửi [] để bỏ hết) + kho mặc định. */
public record AssignWarehousesRequest(
        @NotNull(message = "Thiếu danh sách kho") @Size(max = 100) List<@Valid WarehouseAccessRequest> warehouses,
        @Positive Integer defaultWarehouseId) {
}
