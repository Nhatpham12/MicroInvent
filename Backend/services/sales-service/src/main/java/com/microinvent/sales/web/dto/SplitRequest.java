package com.microinvent.sales.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Tách phiếu xuất theo kho (SRS 3.4.1): chuyển một phần số lượng sang phiếu con xuất ở kho khác. Sản phẩm có serial
 * thì chỉ rõ serial chuyển đi (phải nằm trong các serial của dòng trên phiếu gốc).
 */
public record SplitRequest(
        @NotEmpty(message = "Thiếu danh sách tách phiếu") @Size(max = 20) List<@Valid Allocation> allocations) {

    /** Một phiếu con: kho xuất + các dòng chuyển sang. */
    public record Allocation(@NotNull(message = "Vui lòng chọn kho") Integer warehouseId,
            @NotEmpty(message = "Thiếu dòng chuyển sang kho này") @Size(max = 200) List<@Valid Line> lines) {
    }

    public record Line(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
            List<@NotBlank(message = "Serial trống") String> serials) {
    }
}
