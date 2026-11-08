package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.Warehouse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tạo / sửa kho. Khi sửa, {@code code} bị bỏ qua (mã kho không đổi sau khi tạo). */
public record WarehouseRequest(
        @NotBlank(message = "Vui lòng nhập mã kho") @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "Mã chỉ gồm chữ, số, _ và -") String code,
        @NotBlank(message = "Vui lòng nhập tên kho") @Size(max = 100) String name,
        @NotNull(message = "Vui lòng chọn loại kho") Warehouse.Type type,
        @NotBlank(message = "Vui lòng nhập địa chỉ") @Size(max = 255) String address,
        @Size(max = 100) String province,
        @Size(max = 20) String phone,
        @Email @Size(max = 100) String email,
        Integer managerUserId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Màu dạng #RRGGBB") String color,
        String note) {
}
