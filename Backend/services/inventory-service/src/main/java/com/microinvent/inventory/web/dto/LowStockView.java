package com.microinvent.inventory.web.dto;

/** Sản phẩm dưới ngưỡng tồn tối thiểu tại kho (SRS 3.9). */
public record LowStockView(Integer productId, Integer warehouseId, String warehouseName, int available, int minStock,
        int shortage) {
}
