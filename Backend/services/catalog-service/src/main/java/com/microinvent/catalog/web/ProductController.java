package com.microinvent.catalog.web;

import com.microinvent.catalog.service.ProductService;
import com.microinvent.catalog.web.dto.ProductRequest;
import com.microinvent.catalog.web.dto.ProductResponse;
import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sản phẩm")
@RestController
@RequestMapping("/api/v1/products")
@RequireRoles
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "name", "defaultPriceOut", "defaultPriceIn",
            "createdAt", "updatedAt");

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách sản phẩm: tìm theo mã/tên, lọc nhóm/giá bán/serial, sắp xếp, phân trang",
            description = "sort=name,asc | code | defaultPriceOut | defaultPriceIn | createdAt | updatedAt")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer groupId, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Boolean hasSerial,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.search(
                new ProductService.Filter(keyword, groupId, minPrice, maxPrice, hasSerial),
                PageRequests.of(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id")))));
    }

    @Operation(summary = "Chi tiết sản phẩm")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Tạo sản phẩm")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Sửa sản phẩm (không đổi mã)")
    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<ProductResponse> update(@PathVariable Integer id, @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xóa mềm sản phẩm")
    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
