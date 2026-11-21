package com.microinvent.identity.client;

import com.microinvent.common.web.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** API nội bộ của inventory-service (header X-Internal-Key do InternalFeignInterceptor gắn). */
@FeignClient(name = "inventory-service", url = "${microinvent.clients.inventory-url:}",
        path = "/internal/warehouses")
public interface InventoryClient {

    @PostMapping("/exists")
    ApiResponse<WarehouseExists> exists(@RequestBody WarehouseIds req);

    record WarehouseIds(List<Integer> ids) {
    }

    record WarehouseExists(List<Integer> missing, List<Integer> closed) {
    }
}
