package com.microinvent.catalog.web;

import com.microinvent.catalog.service.SupplierService;
import com.microinvent.catalog.web.dto.SupplierRequest;
import com.microinvent.catalog.web.dto.SupplierResponse;
import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "Nhà cung cấp")
@RestController
@RequestMapping("/api/v1/suppliers")
@RequireRoles({"ADMIN", "MANAGER", "ACCOUNTANT", "STOREKEEPER"})
public class SupplierController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "name", "taxCode", "createdAt", "updatedAt");

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách nhà cung cấp: tìm theo mã/tên/MST/SĐT, sắp xếp, phân trang",
            description = "sort=name,asc | code | taxCode | createdAt | updatedAt. Số tài khoản luôn bị che.")
    @GetMapping
    public ApiResponse<PageResponse<SupplierResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.search(new SupplierService.Filter(keyword),
                PageRequests.of(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id")))));
    }

    @Operation(summary = "Chi tiết nhà cung cấp (ADMIN/MANAGER/ACCOUNTANT thấy đủ số tài khoản)")
    @GetMapping("/{id}")
    public ApiResponse<SupplierResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Tạo nhà cung cấp (mã bỏ trống thì tự sinh; số tài khoản được mã hóa)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"ADMIN", "MANAGER", "ACCOUNTANT"})
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Sửa nhà cung cấp (không đổi mã; bankAccountNo null = giữ nguyên)")
    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER", "ACCOUNTANT"})
    public ApiResponse<SupplierResponse> update(@PathVariable Integer id, @Valid @RequestBody SupplierRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xóa mềm nhà cung cấp")
    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
