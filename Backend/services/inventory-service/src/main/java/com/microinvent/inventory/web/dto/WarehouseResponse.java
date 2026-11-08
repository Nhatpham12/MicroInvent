package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kho kèm tổng tồn thực và giá trị tồn (theo giá vốn bình quân). */
public record WarehouseResponse(Integer id, String code, String name, Warehouse.Type type, String address,
        String province, String phone, String email, Integer managerUserId, Warehouse.Status status,
        boolean isDefault, boolean isStocktakeLocked, String color, String note, long totalQty,
        BigDecimal stockValue, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static WarehouseResponse of(Warehouse w, long totalQty, BigDecimal stockValue) {
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getType(), w.getAddress(),
                w.getProvince(), w.getPhone(), w.getEmail(), w.getManagerUserId(), w.getStatus(),
                w.isDefaultWarehouse(), w.isStocktakeLocked(), w.getColor(), w.getNote(), totalQty, stockValue,
                w.getCreatedAt(), w.getUpdatedAt());
    }
}
