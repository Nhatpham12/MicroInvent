package com.microinvent.notification.web.internal;

import com.microinvent.notification.domain.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Service khác (report, inventory...) tạo cảnh báo cho danh sách người nhận. */
public record CreateNotificationRequest(
        @NotEmpty(message = "Thiếu người nhận") @Size(max = 500) List<@NotNull Integer> userIds,
        Integer warehouseId,
        @NotNull(message = "Thiếu loại cảnh báo") Notification.Type type,
        @NotBlank(message = "Thiếu tiêu đề") @Size(max = 255) String title,
        @Size(max = 5000) String message,
        @Size(max = 30) String refType,
        Integer refId) {
}
