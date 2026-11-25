package com.microinvent.sales.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * API nội bộ tồn kho của inventory-service (X-Internal-Key, tenant, user do InternalFeignInterceptor gắn).
 * Giữ hàng / xuất / nhả cho phiếu xuất: refType EXPORT_SLIP, refId = id phiếu.
 */
@FeignClient(name = "inventory-service", contextId = "inventoryClient", url = "${microinvent.clients.inventory-url:}",
        configuration = DownstreamFeignConfig.class)
public interface InventoryClient {

    /** Tồn khả dụng từng dòng tại 1 kho + gợi ý kho khác còn hàng (không giữ hàng). */
    @PostMapping("/internal/stock/check")
    JsonNode check(@RequestBody CheckRequest req);

    /** Giữ đủ mọi dòng hoặc không giữ dòng nào (thiếu → 409 STOCK_NOT_ENOUGH). */
    @PostMapping("/internal/stock/reserve")
    JsonNode reserve(@RequestHeader("Idempotency-Key") String key, @RequestBody ReserveRequest req);

    /** Trừ tồn thực theo toàn bộ hàng đang giữ của phiếu; trả giá vốn từng sản phẩm. */
    @PostMapping("/internal/stock/commit")
    JsonNode commit(@RequestHeader("Idempotency-Key") String key, @RequestBody CommitRequest req);

    /** Nhả toàn bộ hàng đang giữ của phiếu (không còn gì thì không làm gì). */
    @PostMapping("/internal/stock/release")
    JsonNode release(@RequestHeader("Idempotency-Key") String key, @RequestBody ReleaseRequest req);

    /** Thông tin kho (tên, địa chỉ, SĐT) để in phiếu – API thường, Feign chuyển tiếp người dùng hiện tại. */
    @GetMapping("/api/v1/warehouses/{id}")
    JsonNode warehouse(@PathVariable("id") Integer id);

    record CheckRequest(Integer warehouseId, List<CheckItem> items) {
    }

    record CheckItem(Integer productId, int qty) {
    }

    record ReserveRequest(Integer warehouseId, String refType, Integer refId, Integer userId, Integer customerId,
            LocalDateTime expiresAt, List<ReserveLine> lines) {
    }

    record ReserveLine(Integer productId, int qty, List<String> serials) {
    }

    record CommitRequest(String refType, Integer refId, Integer userId, String movementType) {
    }

    record ReleaseRequest(String refType, Integer refId) {
    }
}
