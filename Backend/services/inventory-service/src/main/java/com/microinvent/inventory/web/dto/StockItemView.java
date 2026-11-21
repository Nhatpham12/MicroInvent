package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Một đơn vị có serial; {@code history} chỉ có khi tra cứu theo serial. */
public record StockItemView(Long id, Integer productId, String serial, Integer warehouseId, Integer lastWarehouseId,
        StockItem.Status status, BigDecimal costPrice, Integer supplierId, Integer receiptId,
        LocalDateTime receivedAt, String currentRefType, Integer currentRefId, List<StockResult.Movement> history) {

    public static StockItemView of(StockItem i, List<StockResult.Movement> history) {
        return new StockItemView(i.getId(), i.getProductId(), i.getSerial(), i.getWarehouseId(),
                i.getLastWarehouseId(), i.getStatus(), i.getCostPrice(), i.getSupplierId(), i.getReceiptId(),
                i.getReceivedAt(), i.getCurrentRefType(), i.getCurrentRefId(), history);
    }
}
