package com.microinvent.notification.web.dto;

import com.microinvent.notification.domain.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(Long id, Notification.Type type, String title, String message,
        Integer warehouseId, String refType, Integer refId, boolean read, boolean resolved,
        LocalDateTime createdAt) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getWarehouseId(),
                n.getRefType(), n.getRefId(), n.isRead(), n.isResolved(), n.getCreatedAt());
    }
}
