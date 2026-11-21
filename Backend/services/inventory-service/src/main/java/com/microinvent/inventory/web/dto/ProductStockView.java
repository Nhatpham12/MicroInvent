package com.microinvent.inventory.web.dto;

import java.util.List;

/** Tồn 1 sản phẩm ở mọi kho được xem (SRS 4.1), kèm tổng cộng. */
public record ProductStockView(Integer productId, Totals totals, List<StockBalanceView> warehouses) {

    public record Totals(int onHand, int hold, int damaged, int available, int inTransitIn, int rentedLoaned) {
    }
}
