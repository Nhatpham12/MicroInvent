package com.microinvent.procurement.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** API nội bộ tồn kho của inventory-service (X-Internal-Key, tenant, user do InternalFeignInterceptor gắn). */
@FeignClient(name = "inventory-service", contextId = "inventoryClient", url = "${microinvent.clients.inventory-url:}",
        configuration = DownstreamFeignConfig.class)
public interface InventoryClient {

    @PostMapping("/internal/stock/receive")
    JsonNode receive(@RequestHeader("Idempotency-Key") String key, @RequestBody StockRequest req);

    @PostMapping("/internal/stock/reverse")
    JsonNode reverse(@RequestHeader("Idempotency-Key") String key, @RequestBody StockRequest req);

    /** Body của receive / reverse; reverse phải gửi đúng body đã receive. */
    record StockRequest(Integer warehouseId, String refType, Integer refId, Integer userId, List<Line> lines) {
    }

    record Line(Integer productId, int qty, List<String> serials, BigDecimal unitCost, Integer supplierId) {
    }
}
