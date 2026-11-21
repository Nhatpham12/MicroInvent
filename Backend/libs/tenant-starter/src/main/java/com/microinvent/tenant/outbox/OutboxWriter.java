package com.microinvent.tenant.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox – ghi event vào bảng {@code outbox_events} của tenant hiện tại, <b>trong cùng transaction</b>
 * với thay đổi nghiệp vụ. {@link OutboxRelay} đẩy lên RabbitMQ sau.
 * <ul>
 * <li>Nghiệp vụ rollback → dòng outbox cũng rollback, không phát event cho thao tác không xảy ra.</li>
 * <li>RabbitMQ lỗi lúc thao tác → event vẫn nằm trong DB, relay gửi lại khi RabbitMQ chạy lại.</li>
 * </ul>
 * Gửi có thể lặp (at-least-once) nên consumer phải chống trùng theo eventId (report-service đã làm qua
 * processed_events).
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param aggregateType PRODUCT, SUPPLIER, RECEIPT...
     * @param aggregateId   id đối tượng (cột INT)
     * @return eventId (UUID) của event vừa ghi
     * @throws IllegalStateException nếu gọi ngoài transaction (event sẽ không gắn với thao tác nghiệp vụ)
     */
    public String append(String eventType, String aggregateType, Integer aggregateId, Map<String, Object> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter.append phải gọi trong @Transactional của nghiệp vụ");
        }
        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outbox_events (event_id, event_type, aggregate_type, aggregate_id, payload, status,
                                           retry_count, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?)
                """, eventId, eventType, aggregateType, aggregateId == null ? 0 : aggregateId, json(payload),
                Timestamp.from(clock.instant()));
        return eventId;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Payload event không chuyển được sang JSON", ex);
        }
    }
}
