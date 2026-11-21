package com.microinvent.procurement.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Đọc sản phẩm ở catalog-service để kiểm tra tồn tại / có serial và chụp snapshot mã, tên, ĐVT. */
@FeignClient(name = "catalog-service", contextId = "catalogClient", url = "${microinvent.clients.catalog-url:}",
        configuration = DownstreamFeignConfig.class)
public interface CatalogClient {

    @GetMapping("/api/v1/products/{id}")
    JsonNode getProduct(@PathVariable("id") Integer id);
}
