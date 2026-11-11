package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockHold;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Giữ hàng tại kho cho chứng từ (refType: EXPORT_SLIP, RENTAL). Thiếu hàng ở bất kỳ dòng nào → 409 STOCK_NOT_ENOUGH,
 * không giữ dòng nào. Dòng có serial thì giữ đúng các serial đó (số serial = qty).
 */
public record StockReserveRequest(@NotNull(message = "Thiếu warehouseId") Integer warehouseId,
        @NotNull(message = "Thiếu refType") StockHold.RefType refType,
        @NotNull(message = "Thiếu refId") Integer refId,
        @NotNull(message = "Thiếu userId") Integer userId,
        Integer customerId,
        LocalDateTime expiresAt,
        @NotEmpty(message = "Danh sách trống") List<@Valid Line> lines) {

    public record Line(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu qty") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
            List<@NotBlank(message = "Serial trống") String> serials) {
    }
}
