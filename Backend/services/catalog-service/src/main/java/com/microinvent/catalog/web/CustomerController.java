package com.microinvent.catalog.web;

import com.microinvent.catalog.domain.Customer;
import com.microinvent.catalog.service.CustomerService;
import com.microinvent.catalog.web.dto.CustomerRequest;
import com.microinvent.catalog.web.dto.CustomerResponse;
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

@Tag(name = "Khách hàng")
@RestController
@RequestMapping("/api/v1/customers")
@RequireRoles
public class CustomerController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "name", "phone", "createdAt", "updatedAt");

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách khách hàng: tìm theo mã/tên/SĐT/MST, lọc loại khách, phân trang",
            description = "sort=name,asc | code | phone | createdAt | updatedAt. CCCD luôn bị che.")
    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Customer.Type customerType,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.search(new CustomerService.Filter(keyword, customerType),
                PageRequests.of(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id")))));
    }

    @Operation(summary = "Chi tiết khách hàng (ADMIN/MANAGER/ACCOUNTANT thấy đủ CCCD)")
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Tạo khách hàng (mã bỏ trống thì tự sinh; CCCD được mã hóa)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Sửa khách hàng (không đổi mã; idCardNo null = giữ nguyên)")
    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER", "SALES", "ACCOUNTANT"})
    public ApiResponse<CustomerResponse> update(@PathVariable Integer id, @Valid @RequestBody CustomerRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xóa mềm khách hàng")
    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
