package com.microinvent.notification.web.internal;

import com.microinvent.common.web.ApiResponse;
import com.microinvent.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ gọi giữa các service (X-Internal-Key + X-Tenant-Id). */
@Tag(name = "Nội bộ – thông báo")
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService service;

    public InternalNotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Tạo cảnh báo cho danh sách người nhận và đẩy realtime")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> create(@Valid @RequestBody CreateNotificationRequest req) {
        return ApiResponse.ok(Map.of("created", service.create(req)));
    }
}
