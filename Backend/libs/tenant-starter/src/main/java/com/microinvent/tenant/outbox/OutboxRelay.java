package com.microinvent.tenant.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.context.TenantContext;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.messaging.EventPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job đẩy {@code outbox_events} PENDING của từng tenant lên RabbitMQ (exchange chung, routing key = event_type).
 * <ul>
 * <li>Mỗi tenant một transaction; {@code FOR UPDATE SKIP LOCKED} để chạy nhiều instance không gửi trùng lô.</li>
 * <li>Gửi lỗi: tăng retry_count, ghi last_error, dừng lô (giữ thứ tự); quá {@code maxRetries} → FAILED.</li>
 * </ul>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Collection<String> tenants;
    private final int batchSize;
    private final int maxRetries;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, EventPublisher publisher, ObjectMapper objectMapper,
            Collection<String> tenants, int batchSize, int maxRetries, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.tenants = List.copyOf(tenants);
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.clock = clock;
    }

    record Row(long id, String eventId, String eventType, int aggregateId, String payload, Timestamp createdAt,
            int retryCount) {
    }

    @Scheduled(fixedDelayString = "${microinvent.outbox.poll-interval:2s}",
            initialDelayString = "${microinvent.outbox.initial-delay:10s}")
    public void relayAll() {
        for (String tenant : tenants) {
            TenantContext.set(tenant);
            try {
                relayTenant(tenant);
            } catch (RuntimeException ex) {
                log.warn("Outbox tenant {}: {}", tenant, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** @return số event đã gửi thành công */
    int relayTenant(String tenant) {
        Integer sent = tx.execute(status -> {
            List<Row> rows = jdbc.query("""
                    SELECT id, event_id, event_type, aggregate_id, payload, created_at, retry_count
                    FROM outbox_events WHERE status = 'PENDING' ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                    """, (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                    rs.getString(5), rs.getTimestamp(6), rs.getInt(7)), batchSize);
            int ok = 0;
            for (Row row : rows) {
                try {
                    publisher.publish(toEvent(tenant, row));
                    jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = ?, last_error = NULL "
                            + "WHERE id = ?", Timestamp.from(clock.instant()), row.id());
                    ok++;
                } catch (RuntimeException ex) {
                    int retries = row.retryCount() + 1;
                    String newStatus = retries >= maxRetries ? "FAILED" : "PENDING";
                    jdbc.update("UPDATE outbox_events SET retry_count = ?, status = ?, last_error = ? WHERE id = ?",
                            retries, newStatus, truncate(String.valueOf(ex.getMessage()), 1000), row.id());
                    log.warn("Outbox {} gửi lỗi lần {} ({}): {}", row.eventId(), retries, newStatus, ex.getMessage());
                    break;
                }
            }
            return ok;
        });
        return sent == null ? 0 : sent;
    }

    private DomainEvent toEvent(String tenant, Row row) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(row.payload(), MAP);
        } catch (Exception ex) {
            throw new IllegalStateException("Payload outbox hỏng: " + ex.getMessage(), ex);
        }
        return new DomainEvent(row.eventId(), row.eventType(), tenant, String.valueOf(row.aggregateId()),
                row.createdAt().toInstant(), payload);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
