package com.microinvent.sales.web.dto;

import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.VatRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tạo / sửa phiếu xuất. {@code exportType} mặc định SALE (bắt buộc customerId). Sản phẩm có serial thì phải chỉ
 * đúng serial xuất (số serial = số lượng). {@code unitPrice} bỏ trống thì lấy giá bán mặc định của sản phẩm.
 */
public record ExportSlipRequest(
        @NotNull(message = "Vui lòng chọn kho xuất") Integer warehouseId,
        ExportSlip.ExportType exportType,
        Integer customerId,
        ExportSlip.DeliveryMethod deliveryMethod,
        @Size(max = 100) String trackingNo,
        VatRate vatRate,
        @DecimalMin(value = "0", message = "Tiền cọc không được âm") @Digits(integer = 16, fraction = 2)
        BigDecimal depositAmount,
        LocalDate dueDate,
        Integer salesUserId,
        @Size(max = 2000) String note,
        @NotEmpty(message = "Phiếu xuất phải có ít nhất 1 dòng") @Size(max = 200) List<@Valid Line> lines) {

    public record Line(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
            @DecimalMin(value = "0", message = "Đơn giá không được âm") @Digits(integer = 16, fraction = 2)
            BigDecimal unitPrice,
            List<@NotBlank(message = "Serial trống") @Size(max = 255) String> serials) {
    }
}
