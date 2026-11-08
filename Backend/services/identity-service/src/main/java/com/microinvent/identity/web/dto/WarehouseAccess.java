package com.microinvent.identity.web.dto;

/** Kho được gán; canWrite = false nghĩa là chỉ xem. */
public record WarehouseAccess(Integer warehouseId, boolean canWrite) {
}
