package com.microinvent.sales.web.dto;

import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.domain.ExportSlipItem;
import com.microinvent.sales.domain.VatRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phiếu xuất. {@code lines} chỉ có ở chi tiết / sau khi tạo, sửa (danh sách trả null cho gọn).
 * {@code shortages} / {@code unavailableSerials} chỉ có khi vừa tạo / sửa mà kho xuất không đủ hàng hoặc serial
 * không nằm ở kho xuất (phiếu lưu PENDING, chưa giữ – cần tách theo kho). {@code splitSlips}: phiếu con vừa tách.
 */
public record ExportSlipResponse(Integer id, String code, ExportSlip.ExportType exportType, Integer warehouseId,
        Integer customerId, Map<String, Object> customer, ExportSlip.Status status, boolean stockReserved,
        LocalDateTime exportDate, ExportSlip.DeliveryMethod deliveryMethod, String trackingNo, VatRate vatRate,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount, BigDecimal depositAmount,
        BigDecimal paidAmount, ExportSlip.PaymentStatus paymentStatus, LocalDate dueDate, Integer salesUserId,
        Integer splitFromId, String note, Integer createdBy, Integer confirmedBy, Integer exportedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<Line> lines, List<Shortage> shortages,
        List<String> unavailableSerials, List<ExportSlipResponse> splitSlips) {

    public record Line(Long id, Integer productId, Map<String, Object> product, int qty, BigDecimal unitPrice,
            BigDecimal unitCost, BigDecimal lineTotal, List<String> serials, Integer warrantyMonths,
            LocalDate warrantyUntil) {

        static Line of(ExportSlipItem i) {
            return new Line(i.getId(), i.getProductId(), i.getProductSnapshot(), i.getQty(), i.getUnitPrice(),
                    i.getUnitCost(), i.getLineTotal(), i.getSerials(), i.getWarrantyMonths(), i.getWarrantyUntil());
        }
    }

    /** Dòng thiếu hàng tại kho xuất + các kho khác còn hàng (để tách phiếu). */
    public record Shortage(Integer productId, int requested, int available, int shortage,
            List<OtherWarehouse> otherWarehouses) {
    }

    public record OtherWarehouse(Integer warehouseId, String warehouseName, int available) {
    }

    /** Kết quả giữ hàng: null = đã giữ đủ; còn lại = dòng thiếu + serial không có sẵn tại kho xuất. */
    public record ReserveOutcome(List<Shortage> shortages, List<String> unavailableSerials) {
    }

    public static ExportSlipResponse of(ExportSlip s, boolean withLines) {
        return of(s, withLines, null, null);
    }

    public static ExportSlipResponse of(ExportSlip s, boolean withLines, ReserveOutcome outcome) {
        return of(s, withLines, outcome, null);
    }

    public static ExportSlipResponse of(ExportSlip s, boolean withLines, ReserveOutcome outcome,
            List<ExportSlipResponse> splitSlips) {
        return new ExportSlipResponse(s.getId(), s.getCode(), s.getExportType(), s.getWarehouseId(),
                s.getCustomerId(), s.getCustomerSnapshot(), s.getStatus(), s.isStockReserved(), s.getExportDate(),
                s.getDeliveryMethod(), s.getTrackingNo(), s.getVatRate(), s.getSubtotal(), s.getVatAmount(),
                s.getTotalAmount(), s.getDepositAmount(), s.getPaidAmount(), s.getPaymentStatus(), s.getDueDate(),
                s.getSalesUserId(), s.getSplitFromId(), s.getNote(), s.getCreatedBy(), s.getConfirmedBy(),
                s.getExportedBy(), s.getCreatedAt(), s.getUpdatedAt(),
                withLines ? s.getItems().stream().map(Line::of).toList() : null,
                outcome == null ? null : outcome.shortages(), outcome == null ? null : outcome.unavailableSerials(),
                splitSlips);
    }
}
