package com.microinvent.procurement.web.dto;

import com.microinvent.procurement.domain.GoodsReceipt;
import com.microinvent.procurement.domain.VatRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tạo / sửa phiếu nhập. {@code sourceType} mặc định SUPPLIER (bắt buộc supplierId); RETAIL_CUSTOMER bắt buộc
 * customerId. Sản phẩm có serial thì số serial phải bằng số lượng.
 */
public record ReceiptRequest(
        @NotNull(message = "Vui lòng chọn kho nhận") Integer warehouseId,
        GoodsReceipt.SourceType sourceType,
        Integer supplierId,
        Integer customerId,
        LocalDateTime receiptDate,
        @Size(max = 100) String contractNo,
        @Size(max = 100) String trackingNo,
        @Size(max = 100) String bankPaymentRef,
        VatRate vatRate,
        @Size(max = 2000) String note,
        @NotEmpty(message = "Phiếu nhập phải có ít nhất 1 dòng") @Size(max = 200) List<@Valid Line> lines) {

    public record Line(@NotNull(message = "Thiếu productId") Integer productId,
            @NotNull(message = "Thiếu số lượng") @Min(value = 1, message = "Số lượng phải >= 1") Integer qty,
            @NotNull(message = "Thiếu đơn giá") @DecimalMin(value = "0", message = "Đơn giá không được âm")
            @Digits(integer = 16, fraction = 2) BigDecimal unitPrice,
            List<@NotBlank(message = "Serial trống") @Size(max = 255) String> serials,
            @Size(max = 255) String conditionNote) {
    }
}
