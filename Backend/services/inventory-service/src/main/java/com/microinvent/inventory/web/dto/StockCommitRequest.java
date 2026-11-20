package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockHold;
import com.microinvent.inventory.domain.StockMovement;
import jakarta.validation.constraints.NotNull;

/**
 * Trừ tồn thực theo toàn bộ hàng đang giữ của chứng từ. {@code movementType}: EXPORT (refType EXPORT_SLIP),
 * RENT / LOAN (refType RENTAL).
 */
public record StockCommitRequest(@NotNull(message = "Thiếu refType") StockHold.RefType refType,
        @NotNull(message = "Thiếu refId") Integer refId,
        @NotNull(message = "Thiếu userId") Integer userId,
        @NotNull(message = "Thiếu movementType") StockMovement.Type movementType) {
}
