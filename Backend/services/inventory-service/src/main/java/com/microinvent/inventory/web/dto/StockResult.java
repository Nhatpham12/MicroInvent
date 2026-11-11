package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockMovement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Kết quả API nội bộ thay đổi tồn: biến động đã ghi, hold đã tạo, giá vốn từng sản phẩm. */
public record StockResult(List<Movement> movements, List<Long> holdIds, List<UnitCost> unitCosts) {

    public record Movement(Long id, Integer productId, Integer warehouseId, StockMovement.Type movementType,
            StockMovement.RefType refType, Integer refId, int qtyChange, int qtyBefore, int qtyAfter,
            BigDecimal unitCost, LocalDateTime createdAt) {

        public static Movement of(StockMovement m) {
            return new Movement(m.getId(), m.getProductId(), m.getWarehouseId(), m.getMovementType(),
                    m.getRefType(), m.getRefId(), m.getQtyChange(), m.getQtyBefore(), m.getQtyAfter(),
                    m.getUnitCost(), m.getCreatedAt());
        }
    }

    public record UnitCost(Integer productId, BigDecimal unitCost) {
    }
}
