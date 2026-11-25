package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockHold;
import java.time.LocalDateTime;

/** Lượt giữ hàng: ai giữ, cho khách nào, đến khi nào (SRS 3.4.2). {@code serial} chỉ có với hàng có serial. */
public record HoldView(Long id, Integer productId, Integer warehouseId, String warehouseName, Long stockItemId,
        String serial, int qty, Integer customerId, String customerName, Integer heldByUserId,
        StockHold.RefType refType, Integer refId, StockHold.Status status, LocalDateTime expiresAt,
        LocalDateTime createdAt) {

    public static HoldView of(StockHold h, String warehouseName, String serial) {
        return new HoldView(h.getId(), h.getProductId(), h.getWarehouseId(), warehouseName, h.getStockItemId(),
                serial, h.getQty(), h.getCustomerId(), h.getCustomerName(), h.getHeldByUserId(), h.getRefType(),
                h.getRefId(), h.getStatus(), h.getExpiresAt(), h.getCreatedAt());
    }
}
