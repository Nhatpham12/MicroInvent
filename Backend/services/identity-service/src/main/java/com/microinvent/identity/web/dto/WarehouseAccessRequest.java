package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** @param canWrite null = true (được ghi); false = chỉ xem */
public record WarehouseAccessRequest(@NotNull(message = "Thiếu mã kho") @Positive Integer warehouseId,
        Boolean canWrite) {

    public boolean writable() {
        return canWrite == null || canWrite;
    }
}
