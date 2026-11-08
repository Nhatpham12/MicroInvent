package com.microinvent.catalog.web;

import com.microinvent.catalog.service.ProductGroupService;
import com.microinvent.catalog.web.dto.ProductGroupRequest;
import com.microinvent.catalog.web.dto.ProductGroupResponse;
import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * Mẫu controller: trả ApiResponse, kiểm vai trò bằng @RequireRoles (common-lib), lỗi nghiệp vụ ném
 * BusinessException để GlobalExceptionHandler trả về đúng khuôn.
 */
@Tag(name = "Nhóm sản phẩm")
@RestController
@RequestMapping("/api/v1/product-groups")
@RequireRoles
public class ProductGroupController {

    private final ProductGroupService service;

    public ProductGroupController(ProductGroupService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách nhóm sản phẩm (kèm số sản phẩm)")
    @GetMapping
    public ApiResponse<List<ProductGroupResponse>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @Operation(summary = "Tạo nhóm sản phẩm")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<ProductGroupResponse> create(@Valid @RequestBody ProductGroupRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Sửa nhóm sản phẩm (không đổi mã)")
    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<ProductGroupResponse> update(@PathVariable Integer id,
            @Valid @RequestBody ProductGroupRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xóa mềm nhóm sản phẩm (chặn nếu còn sản phẩm)")
    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
