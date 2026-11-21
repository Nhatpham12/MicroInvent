package com.microinvent.procurement.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * API nội bộ của catalog-service (X-Internal-Key do InternalFeignInterceptor gắn): lấy sản phẩm / NCC / khách theo
 * danh sách id trong 1 lần gọi, kể cả bản ghi đã xóa mềm (cờ {@code deleted}) và id không tồn tại ({@code notFound}).
 */
@FeignClient(name = "catalog-service", contextId = "catalogClient", url = "${microinvent.clients.catalog-url:}",
        configuration = DownstreamFeignConfig.class)
public interface CatalogClient {

    /** Trả {@code data.products[]} (id, code, name, unit, hasSerial, deleted…) và {@code data.notFound[]}. */
    @PostMapping("/internal/products/batch")
    JsonNode products(@RequestBody ProductIds req);

    /** Trả {@code data.suppliers[]}, {@code data.customers[]}, {@code data.supplierIdsNotFound[]}, {@code data.customerIdsNotFound[]}. */
    @PostMapping("/internal/partners/batch")
    JsonNode partners(@RequestBody PartnerIds req);

    record ProductIds(List<Integer> ids) {
    }

    record PartnerIds(List<Integer> supplierIds, List<Integer> customerIds) {
    }
}
