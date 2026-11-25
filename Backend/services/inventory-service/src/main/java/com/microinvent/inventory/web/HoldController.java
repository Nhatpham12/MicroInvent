package com.microinvent.inventory.web;

import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import com.microinvent.inventory.domain.StockHold;
import com.microinvent.inventory.service.HoldService;
import com.microinvent.inventory.web.dto.HoldExtendRequest;
import com.microinvent.inventory.web.dto.HoldRequest;
import com.microinvent.inventory.web.dto.HoldView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Giữ hàng tại kho (SRS 3.4.2) – API #31–34; quyền kho kiểm theo kho của lượt giữ. */
@Tag(name = "Giữ hàng")
@RestController
@RequestMapping("/api/v1/holds")
@RequireRoles
public class HoldController {

    private static final Set<String> SORTABLE = Set.of("id", "expiresAt", "createdAt", "productId", "warehouseId");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    private final HoldService service;

    public HoldController(HoldService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách giữ hàng: ai giữ, cho khách nào, đến khi nào (lọc kho, SP, khách, trạng thái, loại)")
    @GetMapping
    public ApiResponse<PageResponse<HoldView>> list(@RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) Integer productId, @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) StockHold.Status status,
            @RequestParam(required = false) StockHold.RefType refType,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.list(warehouseId, productId, customerId, status, refType,
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Giữ hàng tại kho cho khách (trừ tồn khả dụng); hàng có serial: mỗi serial 1 lượt giữ")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"SALES", "STOREKEEPER", "MANAGER"})
    public ApiResponse<List<HoldView>> create(@Valid @RequestBody HoldRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Gia hạn giữ hàng (chỉ lượt giữ thủ công còn hiệu lực)")
    @PatchMapping("/{id}/extend")
    @RequireRoles({"SALES", "MANAGER"})
    public ApiResponse<HoldView> extend(@PathVariable Long id, @Valid @RequestBody HoldExtendRequest req) {
        return ApiResponse.ok(service.extend(id, req));
    }

    @Operation(summary = "Nhả hàng đang giữ (giữ cho chứng từ thì nhả qua chứng từ)")
    @DeleteMapping("/{id}")
    @RequireRoles({"SALES", "STOREKEEPER", "MANAGER"})
    public ApiResponse<HoldView> release(@PathVariable Long id) {
        return ApiResponse.ok(service.release(id));
    }
}
