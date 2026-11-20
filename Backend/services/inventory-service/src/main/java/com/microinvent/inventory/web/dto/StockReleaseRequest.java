package com.microinvent.inventory.web.dto;

import com.microinvent.inventory.domain.StockHold;
import jakarta.validation.constraints.NotNull;

/** Nhả toàn bộ hàng đang giữ của chứng từ (hủy / từ chối). Không còn gì để nhả thì trả kết quả rỗng. */
public record StockReleaseRequest(@NotNull(message = "Thiếu refType") StockHold.RefType refType,
        @NotNull(message = "Thiếu refId") Integer refId) {
}
