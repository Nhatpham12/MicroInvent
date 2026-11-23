package com.microinvent.sales.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import com.microinvent.sales.domain.ExportSlip;
import com.microinvent.sales.service.ExportSlipPdfService;
import com.microinvent.sales.service.ExportSlipService;
import com.microinvent.sales.web.dto.AvailabilityRequest;
import com.microinvent.sales.web.dto.ExportSlipRequest;
import com.microinvent.sales.web.dto.ExportSlipResponse;
import com.microinvent.sales.web.dto.SplitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu xuất kho (SRS 3.4); quyền kho kiểm theo kho xuất của phiếu. Vai trò theo sheet API #36–45. */
@Tag(name = "Phiếu xuất")
@RestController
@RequestMapping("/api/v1/exports")
@RequireRoles
public class ExportSlipController {

    private static final Set<String> SORTABLE = Set.of("id", "code", "createdAt", "exportDate", "totalAmount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ExportSlipService service;
    private final ExportSlipPdfService pdf;

    public ExportSlipController(ExportSlipService service, ExportSlipPdfService pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @Operation(summary = "Danh sách phiếu xuất (lọc kho, khách, trạng thái, loại, khoảng ngày tạo, mã phiếu)")
    @GetMapping
    public ApiResponse<PageResponse<ExportSlipResponse>> list(@RequestParam(required = false) Integer warehouseId,
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) ExportSlip.Status status,
            @RequestParam(required = false) ExportSlip.ExportType exportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.list(warehouseId, customerId, status, exportType, from, to, q,
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "Kiểm tồn khả dụng tại kho xuất + kho khác còn hàng (không giữ hàng)")
    @PostMapping("/check-availability")
    public ApiResponse<JsonNode> checkAvailability(@Valid @RequestBody AvailabilityRequest req) {
        return ApiResponse.ok(service.checkAvailability(req));
    }

    @Operation(summary = "Tạo phiếu xuất (giữ hàng tại kho xuất; thiếu hàng → lưu chưa giữ, trả shortages để tách phiếu)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRoles({"STOREKEEPER", "MANAGER", "SALES"})
    public ApiResponse<ExportSlipResponse> create(@Valid @RequestBody ExportSlipRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Chi tiết phiếu xuất")
    @GetMapping("/{id}")
    public ApiResponse<ExportSlipResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "In phiếu xuất PDF (tên + địa chỉ kho xuất, khách, dòng hàng, serial, bảo hành)")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer id) {
        ExportSlipPdfService.Pdf file = pdf.render(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.code() + ".pdf").build().toString())
                .body(file.content());
    }

    @Operation(summary = "Sửa phiếu khi chưa xuất (giữ lại hàng theo dòng mới; phiếu đã xác nhận quay về chờ xác nhận)")
    @PutMapping("/{id}")
    @RequireRoles({"STOREKEEPER", "MANAGER", "SALES"})
    public ApiResponse<ExportSlipResponse> update(@PathVariable Integer id, @Valid @RequestBody ExportSlipRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Tách phiếu theo kho: chuyển một phần số lượng / serial sang phiếu con ở kho khác rồi giữ hàng lại")
    @PostMapping("/{id}/split")
    @RequireRoles({"STOREKEEPER", "MANAGER", "SALES"})
    public ApiResponse<ExportSlipResponse> split(@PathVariable Integer id, @Valid @RequestBody SplitRequest req) {
        return ApiResponse.ok(service.split(id, req));
    }

    @Operation(summary = "Xác nhận: chờ xác nhận → đã xác nhận (phải giữ đủ hàng)")
    @PostMapping("/{id}/confirm")
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ExportSlipResponse> confirm(@PathVariable Integer id) {
        return ApiResponse.ok(service.confirm(id));
    }

    @Operation(summary = "Xuất kho: trừ tồn, ghi giá vốn + bảo hành, phát export.exported")
    @PostMapping("/{id}/export")
    @RequireRoles({"STOREKEEPER"})
    public ApiResponse<ExportSlipResponse> export(@PathVariable Integer id) {
        return ApiResponse.ok(service.export(id));
    }

    @Operation(summary = "Hủy phiếu chưa xuất, nhả hàng đang giữ")
    @PostMapping("/{id}/cancel")
    @RequireRoles({"STOREKEEPER", "MANAGER"})
    public ApiResponse<ExportSlipResponse> cancel(@PathVariable Integer id) {
        return ApiResponse.ok(service.cancel(id));
    }
}
