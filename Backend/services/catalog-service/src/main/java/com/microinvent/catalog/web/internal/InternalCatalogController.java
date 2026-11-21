package com.microinvent.catalog.web.internal;

import com.microinvent.catalog.service.InternalCatalogService;
import com.microinvent.catalog.web.internal.InternalBatchDtos.PartnerBatchRequest;
import com.microinvent.catalog.web.internal.InternalBatchDtos.PartnerBatchResponse;
import com.microinvent.catalog.web.internal.InternalBatchDtos.ProductBatchRequest;
import com.microinvent.catalog.web.internal.InternalBatchDtos.ProductBatchResponse;
import com.microinvent.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chỉ gọi giữa các service (header X-Internal-Key, InternalApiKeyFilter kiểm). Gateway không route /internal/**.
 * Dùng POST để gửi danh sách id dài mà không vượt giới hạn URL.
 */
@Tag(name = "Nội bộ – catalog")
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private final InternalCatalogService service;

    public InternalCatalogController(InternalCatalogService service) {
        this.service = service;
    }

    @Operation(summary = "Lấy nhiều sản phẩm theo danh sách id (kể cả đã xóa mềm) để chụp snapshot chứng từ")
    @PostMapping("/products/batch")
    public ApiResponse<ProductBatchResponse> products(@Valid @RequestBody ProductBatchRequest req) {
        return ApiResponse.ok(service.products(req.ids()));
    }

    @Operation(summary = "Lấy nhà cung cấp / khách hàng theo danh sách id (kể cả đã xóa mềm)")
    @PostMapping("/partners/batch")
    public ApiResponse<PartnerBatchResponse> partners(@Valid @RequestBody PartnerBatchRequest req) {
        return ApiResponse.ok(service.partners(req.supplierIds(), req.customerIds()));
    }
}
