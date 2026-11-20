package com.microinvent.inventory.web;

import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.StockItem;
import com.microinvent.inventory.domain.StockMovement;
import com.microinvent.inventory.service.StockQueryService;
import com.microinvent.inventory.web.dto.LowStockView;
import com.microinvent.inventory.web.dto.ProductStockView;
import com.microinvent.inventory.web.dto.StockBalanceView;
import com.microinvent.inventory.web.dto.StockItemView;
import com.microinvent.inventory.web.dto.StockResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tra cứu tồn kho (SRS 3.10.4); kho lọc theo phạm vi kho của người dùng. */
@Tag(name = "Tồn kho")
@RestController
@RequestMapping("/api/v1/stocks")
@RequireRoles
public class StockController {

    private final StockQueryService service;

    public StockController(StockQueryService service) {
        this.service = service;
    }

    @Operation(summary = "Tồn theo (sản phẩm, kho): thực, giữ, hỏng, khả dụng, đang đến, đang thuê")
    @GetMapping
    public ApiResponse<PageResponse<StockBalanceView>> list(@RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) Integer productId, @RequestParam(required = false) Boolean hasStock,
            @PageableDefault(size = 20, sort = {"productId", "warehouseId"}) Pageable pageable) {
        return ApiResponse.ok(service.list(warehouseId, productId, hasStock, pageable));
    }

    @Operation(summary = "Tồn 1 sản phẩm ở tất cả kho được xem, kèm tổng")
    @GetMapping("/products/{productId}")
    public ApiResponse<ProductStockView> byProduct(@PathVariable Integer productId) {
        return ApiResponse.ok(service.byProduct(productId));
    }

    @Operation(summary = "Tra cứu serial: kho hiện tại / đang chuyển / đang thuê / đã bán, kèm lịch sử")
    @GetMapping("/serials/{serial}")
    public ApiResponse<StockItemView> bySerial(@PathVariable String serial) {
        return ApiResponse.ok(service.bySerial(serial));
    }

    @Operation(summary = "Danh sách đơn vị có serial theo kho / sản phẩm / trạng thái")
    @GetMapping("/items")
    public ApiResponse<PageResponse<StockItemView>> items(@RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) Integer productId, @RequestParam(required = false) StockItem.Status status,
            @RequestParam(required = false) String q, @PageableDefault(size = 20, sort = "serial") Pageable pageable) {
        return ApiResponse.ok(service.listItems(warehouseId, productId, status, q, pageable));
    }

    @Operation(summary = "Sản phẩm dưới ngưỡng tồn tối thiểu theo kho")
    @GetMapping("/low")
    public ApiResponse<List<LowStockView>> low(@RequestParam(required = false) Integer warehouseId) {
        return ApiResponse.ok(service.lowStock(warehouseId));
    }

    @Operation(summary = "Nhật ký biến động tồn (lọc kho, sản phẩm, loại, khoảng ngày)")
    @GetMapping("/movements")
    @RequireRoles({"ADMIN", "MANAGER", "ACCOUNTANT", "STOREKEEPER"})
    public ApiResponse<PageResponse<StockResult.Movement>> movements(
            @RequestParam(required = false) Integer warehouseId, @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) StockMovement.Type type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(service.listMovements(warehouseId, productId, type, from, to, pageable));
    }
}
