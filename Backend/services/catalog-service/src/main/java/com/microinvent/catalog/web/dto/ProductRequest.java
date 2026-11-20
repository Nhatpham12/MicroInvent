package com.microinvent.catalog.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Tạo / sửa sản phẩm. Khi sửa, code bị bỏ qua (mã không đổi sau khi tạo). Ảnh cập nhật qua API upload riêng.
 *
 * @param hasSerial null = false
 */
public record ProductRequest(
        @NotBlank(message = "Vui lòng nhập mã sản phẩm") @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.\\-]+$", message = "Mã chỉ gồm chữ, số, dấu chấm, _ và -") String code,
        @NotBlank(message = "Vui lòng nhập tên sản phẩm") @Size(max = 255) String name,
        @NotNull(message = "Vui lòng chọn nhóm sản phẩm") @Positive Integer groupId,
        @NotBlank(message = "Vui lòng nhập đơn vị tính") @Size(max = 20) String unit,
        @DecimalMin(value = "0", message = "Giá không được âm") @Digits(integer = 16, fraction = 2)
        BigDecimal defaultPriceIn,
        @DecimalMin(value = "0", message = "Giá không được âm") @Digits(integer = 16, fraction = 2)
        BigDecimal defaultPriceOut,
        @DecimalMin(value = "0", message = "Giá không được âm") @Digits(integer = 16, fraction = 2)
        BigDecimal defaultRentPricePerDay,
        @PositiveOrZero(message = "Ngưỡng tồn không được âm") Integer minStock,
        Boolean hasSerial,
        @PositiveOrZero @Max(value = 240, message = "Bảo hành tối đa 240 tháng") Integer warrantyMonths,
        @Size(max = 5000) String description) {
}
