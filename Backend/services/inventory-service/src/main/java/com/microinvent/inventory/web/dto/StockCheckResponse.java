package com.microinvent.inventory.web.dto;

import java.util.List;

/** {@code enough = false} khi có dòng thiếu; kèm tồn khả dụng ở các kho khác để gợi ý tách phiếu / chuyển kho. */
public record StockCheckResponse(boolean enough, List<Line> lines) {

    public record Line(Integer productId, int requested, int available, int shortage,
            List<OtherWarehouse> otherWarehouses) {
    }

    public record OtherWarehouse(Integer warehouseId, String warehouseName, int available) {
    }
}
