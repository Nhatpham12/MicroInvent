package com.microinvent.notification.service;

import com.microinvent.notification.web.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Đẩy cảnh báo realtime qua STOMP tới đúng người: client subscribe {@code /user/queue/notifications}.
 * Tên principal của phiên WebSocket là {@code <tenant>:<userId>} (xem WebSocketConfig) để không lẫn giữa tenant.
 */
@Component
public class NotificationPusher {

    public static final String QUEUE = "/queue/notifications";
    private static final Logger log = LoggerFactory.getLogger(NotificationPusher.class);

    private final SimpMessagingTemplate template;

    public NotificationPusher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public static String principalName(String tenant, Object userId) {
        return tenant + ":" + userId;
    }

    public void push(String tenant, Integer userId, NotificationResponse payload) {
        try {
            template.convertAndSendToUser(principalName(tenant, userId), QUEUE, payload);
        } catch (RuntimeException ex) {
            // Đẩy realtime lỗi không làm hỏng việc lưu: người dùng vẫn thấy khi tải lại danh sách.
            log.warn("Không đẩy được cảnh báo {} tới user {}: {}", payload.id(), userId, ex.getMessage());
        }
    }
}
