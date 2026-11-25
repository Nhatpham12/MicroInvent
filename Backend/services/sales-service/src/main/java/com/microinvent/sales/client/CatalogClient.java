package com.microinvent.sales.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * API nội bộ của catalog-service: lấy sản phẩm / khách theo danh sách id trong 1 lần gọi, kể cả bản ghi đã xóa mềm
 * (cờ {@code deleted}) và id không tồn tại ({@code notFound}).
 */
@FeignClient(name = "catalog-service", contextId = "catalogClient", url = "${microinvent.clients.catalog-url:}",
        configuration = DownstreamFeignConfig.class)
public interface CatalogClient {

    /** Trả {@code data.products[]} (id, code, name, unit, hasSerial, warrantyMonths, defaultPriceOut, deleted…). */
    @PostMapping("/internal/products/batch")
    JsonNode products(@RequestBody ProductIds req);

    /** Trả {@code data.customers[]} (id, code, name, customerType, taxCode, address, phone, deleted…). */
    @PostMapping("/internal/partners/batch")
    JsonNode partners(@RequestBody PartnerIds req);

    record ProductIds(List<Integer> ids) {
    }

    record PartnerIds(List<Integer> supplierIds, List<Integer> customerIds) {
    }
}
