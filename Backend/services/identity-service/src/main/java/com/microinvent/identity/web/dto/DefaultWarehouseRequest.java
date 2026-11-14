package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DefaultWarehouseRequest(@NotNull(message = "Vui lòng chọn kho") @Positive Integer warehouseId) {
}
