package com.microinvent.notification.web;

import com.microinvent.common.security.RequireRoles;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import com.microinvent.notification.domain.Notification;
import com.microinvent.notification.service.NotificationService;
import com.microinvent.notification.web.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cảnh báo của người dùng đang đăng nhập. Realtime: kết nối STOMP {@code ws://<gateway>/ws?access_token=...}
 * rồi subscribe {@code /user/queue/notifications}.
 */
@Tag(name = "Thông báo")
@RestController
@RequestMapping("/api/v1/notifications")
@RequireRoles
public class NotificationController {

    private static final Set<String> SORTABLE = Set.of("id", "createdAt");

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách cảnh báo của tôi (mới nhất trước)",
            description = "Lọc unread=true, resolved=false, type, warehouseId. Cảnh báo của kho không còn được gán bị ẩn.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) Notification.Type type,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.list(new NotificationService.Filter(unread, resolved, type, warehouseId),
                PageRequests.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
    }

    @Operation(summary = "Số cảnh báo chưa đọc (chuông)")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", service.unreadCount()));
    }

    @Operation(summary = "Đánh dấu tất cả đã đọc")
    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll() {
        return ApiResponse.ok(Map.of("updated", service.markAllRead()));
    }

    @Operation(summary = "Đánh dấu đã đọc")
    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> read(@PathVariable Long id) {
        return ApiResponse.ok(service.markRead(id));
    }

    @Operation(summary = "Đánh dấu đã xử lý")
    @PatchMapping("/{id}/resolve")
    public ApiResponse<NotificationResponse> resolve(@PathVariable Long id) {
        return ApiResponse.ok(service.resolve(id));
    }
}
