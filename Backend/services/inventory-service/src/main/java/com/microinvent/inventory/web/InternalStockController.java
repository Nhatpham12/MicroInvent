package com.microinvent.inventory.web;

import com.microinvent.common.idempotency.IdempotencyService;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.HeaderNames;
import com.microinvent.inventory.service.InternalStockService;
import com.microinvent.inventory.web.dto.StockCheckRequest;
import com.microinvent.inventory.web.dto.StockCheckResponse;
import com.microinvent.inventory.web.dto.StockReceiveRequest;
import com.microinvent.inventory.web.dto.StockResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ cho procurement / sales / rental-service (X-Internal-Key kiểm bởi InternalApiKeyFilter).
 * Thao tác thay đổi tồn bắt buộc header Idempotency-Key: gọi lại cùng key trả lại kết quả cũ, không cộng/trừ lần 2.
 */
@Tag(name = "Internal – tồn kho")
@RestController
@RequestMapping("/internal/stock")
public class InternalStockController {

    private final InternalStockService service;
    private final IdempotencyService idempotency;

    public InternalStockController(InternalStockService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Kiểm tồn khả dụng nhiều dòng + gợi ý kho khác")
    @PostMapping("/check")
    public ApiResponse<StockCheckResponse> check(@Valid @RequestBody StockCheckRequest req) {
        return ApiResponse.ok(service.check(req));
    }

    @Operation(summary = "Cộng tồn khi nhập kho / nhận hàng trả")
    @PostMapping("/receive")
    public ApiResponse<StockResult> receive(@RequestHeader(HeaderNames.IDEMPOTENCY_KEY) String key,
            @Valid @RequestBody StockReceiveRequest req) {
        return ApiResponse.ok(idempotency.execute("stock.receive", key, StockResult.class, () -> service.receive(req)));
    }
}
