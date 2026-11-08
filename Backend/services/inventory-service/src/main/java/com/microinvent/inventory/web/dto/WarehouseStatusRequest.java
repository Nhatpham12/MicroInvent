package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.Warehouse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WarehouseStatusRequest(@NotNull(message = "Vui lòng chọn trạng thái") Warehouse.Status status,
        @Size(max = 255) String reason) {
}
