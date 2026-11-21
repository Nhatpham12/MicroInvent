package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockBalance;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tồn của 1 sản phẩm tại 1 kho; {@code available = onHand − hold − damaged}. */
public record StockBalanceView(Integer productId, Integer warehouseId, String warehouseName, int onHand, int hold,
        int damaged, int available, int inTransitIn, int rentedLoaned, BigDecimal avgCost, BigDecimal stockValue,
        LocalDateTime updatedAt) {

    public static StockBalanceView of(StockBalance b, String warehouseName) {
        return new StockBalanceView(b.getProductId(), b.getWarehouseId(), warehouseName, b.getQtyOnHand(),
                b.getQtyHold(), b.getQtyDamaged(), b.available(), b.getQtyInTransitIn(), b.getQtyRentedLoaned(),
                b.getAvgCost(), b.getAvgCost().multiply(BigDecimal.valueOf(b.getQtyOnHand())), b.getUpdatedAt());
    }
}
