package com.microinvent.inventory.web.dto;

public record MinStockResponse(Integer productId, Integer warehouseId, int minStock) {
}
