package com.microinvent.inventory.web;

import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.Warehouse;
import com.microinvent.inventory.service.WarehouseService;
import com.microinvent.inventory.web.dto.CloseCheckResponse;
import com.microinvent.inventory.web.dto.MinStockRequest;
import com.microinvent.inventory.web.dto.MinStockResponse;
import com.microinvent.inventory.web.dto.WarehouseRequest;
import com.microinvent.inventory.web.dto.WarehouseResponse;
import com.microinvent.inventory.web.dto.WarehouseStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục kho (SRS 3.10.1) và ngưỡng tồn theo kho (SRS 3.10.4). */
@Tag(name = "Kho")
@RestController
@RequestMapping("/api/v1/warehouses")
@RequireRoles
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách kho (kèm tổng tồn, giá trị tồn); thủ kho/sales chỉ thấy kho được gán")
    @GetMapping
    public ApiResponse<PageResponse<WarehouseResponse>> list(@RequestParam(required = false) Warehouse.Type type,
            @RequestParam(required = false) Warehouse.Status status, @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ApiResponse.ok(service.list(type, status, q, pageable));
    }

    @Operation(summary = "Tạo kho")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles("ADMIN")
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Chi tiết kho")
    @GetMapping("/{id}")
    public ApiResponse<WarehouseResponse> detail(@PathVariable Integer id) {
        return ApiResponse.ok(service.detail(id));
    }

    @Operation(summary = "Sửa thông tin kho, thủ kho phụ trách, màu nhận diện (không đổi mã)")
    @PutMapping("/{id}")
    @RequireRoles("ADMIN")
    public ApiResponse<WarehouseResponse> update(@PathVariable Integer id, @Valid @RequestBody WarehouseRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Kiểm tra điều kiện ngừng hoạt động kho")
    @GetMapping("/{id}/close-check")
    @RequireRoles("ADMIN")
    public ApiResponse<CloseCheckResponse> closeCheck(@PathVariable Integer id) {
        return ApiResponse.ok(service.closeCheck(id));
    }

    @Operation(summary = "Đổi trạng thái ACTIVE / SUSPENDED / CLOSED")
    @PatchMapping("/{id}/status")
    @RequireRoles("ADMIN")
    public ApiResponse<WarehouseResponse> changeStatus(@PathVariable Integer id,
            @Valid @RequestBody WarehouseStatusRequest req) {
        return ApiResponse.ok(service.changeStatus(id, req.status()));
    }

    @Operation(summary = "Đặt kho mặc định hệ thống")
    @PatchMapping("/{id}/default")
    @RequireRoles("ADMIN")
    public ApiResponse<WarehouseResponse> setDefault(@PathVariable Integer id) {
        return ApiResponse.ok(service.setDefault(id));
    }

    @Operation(summary = "Ngưỡng tồn tối thiểu theo sản phẩm tại kho")
    @GetMapping("/{id}/min-stock")
    @RequireRoles({"ADMIN", "MANAGER", "STOREKEEPER"})
    public ApiResponse<List<MinStockResponse>> listMinStock(@PathVariable Integer id) {
        return ApiResponse.ok(service.listMinStock(id));
    }

    @Operation(summary = "Đặt ngưỡng tồn hàng loạt (minStock = null để xóa ngưỡng riêng)")
    @PutMapping("/{id}/min-stock")
    @RequireRoles({"ADMIN", "MANAGER"})
    public ApiResponse<List<MinStockResponse>> updateMinStock(@PathVariable Integer id,
            @Valid @RequestBody MinStockRequest req) {
        return ApiResponse.ok(service.updateMinStock(id, req));
    }
}
