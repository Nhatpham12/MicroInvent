package com.microinvent.inventory.web.dto;

import java.util.List;

/** Kết quả kiểm tra điều kiện ngừng hoạt động kho (SRS 3.10.1). */
public record CloseCheckResponse(boolean canClose, List<Blocker> blockers) {

    public enum BlockerType { STOCK_REMAINING, IN_TRANSIT, OPEN_DOCUMENTS, ACTIVE_HOLDS, ACTIVE_RENTALS }

    public record Blocker(BlockerType type, long count, String message) {
    }
}
