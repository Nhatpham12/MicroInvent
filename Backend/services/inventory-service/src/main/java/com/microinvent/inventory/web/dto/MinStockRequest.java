package com.microinvent.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Đặt ngưỡng tồn hàng loạt; {@code minStock = null} để xóa ngưỡng riêng (dùng ngưỡng chung của sản phẩm).
 * Lưu ý: thiếu hẳn trường {@code minStock} cũng bị hiểu là null → xóa ngưỡng, nên client phải luôn gửi trường này.
 */
public record MinStockRequest(@NotEmpty(message = "Danh sách trống") List<@Valid Item> items) {

    public record Item(@NotNull(message = "Thiếu productId") Integer productId,
            @Min(value = 0, message = "Ngưỡng không được âm") Integer minStock) {
    }
}
