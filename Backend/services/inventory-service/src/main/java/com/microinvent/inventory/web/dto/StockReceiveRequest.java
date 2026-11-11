package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockMovement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cộng tồn khi nhập kho / nhận hàng trả (SRS 3.3.1). {@code refType}: RECEIPT, RENTAL_RETURN, SALES_RETURN.
 * Dòng có serial thì số serial phải bằng {@code qty}.
 */
public record StockReceiveRequest(@NotNull(message = "Thiếu warehouseId") Integer warehouseId,
        @NotNull(message = "Thiếu refType") StockMovement.RefType refType,
        @NotNull(message = "Thiếu refId") Integer refId,
        @NotNull(message = "Thiếu userId") Integer userId,
        @NotEmpty(message = "Danh sách trống") List<@Valid Line> lines) {

    public enum Condition { GOOD, DAMAGED }

    public record Line(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu qty") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
            List<@NotBlank(message = "Serial trống") String> serials,
            @DecimalMin(value = "0", message = "Giá vốn không được âm") BigDecimal unitCost,
            Integer supplierId,
            Condition condition) {
    }
}
