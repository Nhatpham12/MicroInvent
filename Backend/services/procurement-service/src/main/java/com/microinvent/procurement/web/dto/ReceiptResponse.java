package com.microinvent.procurement.web.dto;

import com.microinvent.procurement.domain.GoodsReceipt;
import com.microinvent.procurement.domain.GoodsReceiptItem;
import com.microinvent.procurement.domain.VatRate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Phiếu nhập; {@code lines} chỉ có ở chi tiết / sau khi tạo, sửa (danh sách trả null cho gọn). */
public record ReceiptResponse(Integer id, String code, Integer warehouseId, GoodsReceipt.SourceType sourceType,
        Integer supplierId, Integer customerId, LocalDateTime receiptDate, GoodsReceipt.Status status,
        String contractNo, String trackingNo, String bankPaymentRef, VatRate vatRate, BigDecimal subtotal,
        BigDecimal vatAmount, BigDecimal totalAmount, BigDecimal paidAmount, GoodsReceipt.PaymentStatus paymentStatus,
        String note, Integer createdBy, Integer confirmedBy, LocalDateTime confirmedAt, LocalDateTime createdAt,
        LocalDateTime updatedAt, List<Line> lines) {

    public record Line(Long id, Integer productId, Map<String, Object> product, int qty, BigDecimal unitPrice,
            BigDecimal lineTotal, List<String> serials, String conditionNote) {

        static Line of(GoodsReceiptItem i) {
            return new Line(i.getId(), i.getProductId(), i.getProductSnapshot(), i.getQty(), i.getUnitPrice(),
                    i.getLineTotal(), i.getSerials(), i.getConditionNote());
        }
    }

    public static ReceiptResponse of(GoodsReceipt r, boolean withLines) {
        return new ReceiptResponse(r.getId(), r.getCode(), r.getWarehouseId(), r.getSourceType(), r.getSupplierId(),
                r.getCustomerId(), r.getReceiptDate(), r.getStatus(), r.getContractNo(), r.getTrackingNo(),
                r.getBankPaymentRef(), r.getVatRate(), r.getSubtotal(), r.getVatAmount(), r.getTotalAmount(),
                r.getPaidAmount(), r.getPaymentStatus(), r.getNote(), r.getCreatedBy(), r.getConfirmedBy(),
                r.getConfirmedAt(), r.getCreatedAt(), r.getUpdatedAt(),
                withLines ? r.getItems().stream().map(Line::of).toList() : null);
    }
}
