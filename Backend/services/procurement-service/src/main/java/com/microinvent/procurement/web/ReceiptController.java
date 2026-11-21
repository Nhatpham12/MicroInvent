package com.microinvent.procurement.web;

import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageResponse;
import com.microinvent.procurement.domain.GoodsReceipt;
import com.microinvent.procurement.service.ReceiptService;
import com.microinvent.procurement.web.dto.ReceiptRequest;
import com.microinvent.procurement.web.dto.ReceiptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu nhập kho (SRS 3.3); quyền kho kiểm theo kho của phiếu. */
@Tag(name = "Phiếu nhập")
@RestController
@RequestMapping("/api/v1/receipts")
@RequireRoles({"ADMIN", "MANAGER", "ACCOUNTANT", "STOREKEEPER"})
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách phiếu nhập (lọc kho, NCC, trạng thái, khoảng ngày, mã / số HĐ)")
    @GetMapping
    public ApiResponse<PageResponse<ReceiptResponse>> list(@RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) Integer supplierId,
            @RequestParam(required = false) GoodsReceipt.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = {"receiptDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(service.list(warehouseId, supplierId, status, from, to, q, pageable));
    }

    @Operation(summary = "Tạo phiếu nhập nháp (kho nhận bắt buộc)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Chi tiết phiếu nhập")
    @GetMapping("/{id}")
    public ApiResponse<ReceiptResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Sửa phiếu (đã xác nhận thì đảo tồn theo dòng cũ, cộng theo dòng mới)")
    @PutMapping("/{id}")
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ReceiptResponse> update(@PathVariable Integer id, @Valid @RequestBody ReceiptRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Xác nhận → cộng tồn kho nhận, phát receipt.confirmed")
    @PostMapping("/{id}/confirm")
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ReceiptResponse> confirm(@PathVariable Integer id) {
        return ApiResponse.ok(service.confirm(id));
    }

    @Operation(summary = "Hủy phiếu (đã xác nhận thì đảo tồn)")
    @PostMapping("/{id}/cancel")
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ReceiptResponse> cancel(@PathVariable Integer id) {
        return ApiResponse.ok(service.cancel(id));
    }
}
