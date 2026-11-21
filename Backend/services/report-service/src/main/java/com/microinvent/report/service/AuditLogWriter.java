package com.microinvent.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.event.DomainEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi audit_logs từ event audit.log. Chống xử lý trùng bằng bảng processed_events (INSERT IGNORE theo eventId)
 * trong cùng transaction với dòng nhật ký. Dùng JDBC thay vì JPA vì cột before/after là JSON.
 */
@Service
public class AuditLogWriter {

    static final int MAX_ACTION = 60;
    static final int MAX_ENTITY = 60;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** @return false nếu event đã xử lý trước đó (bỏ qua) */
    @Transactional
    public boolean write(DomainEvent event) {
        int fresh = jdbc.update("INSERT IGNORE INTO processed_events (event_id) VALUES (?)", event.eventId());
        if (fresh == 0) {
            return false;
        }
        Map<String, Object> p = event.payload() == null ? Map.of() : event.payload();
        String action = required(p, "action", MAX_ACTION);
        String entity = required(p, "entity", MAX_ENTITY);
        String service = truncate(str(p.get("service")), 30);
        jdbc.update("""
                INSERT INTO audit_logs (user_id, username, warehouse_id, service, action, entity, entity_id,
                                        before_data, after_data, ip_address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                toInt(p.get("userId")), truncate(str(p.get("username")), 50), toInt(p.get("warehouseId")),
                service == null ? "unknown" : service, action, entity,
                truncate(str(p.getOrDefault("entityId", event.aggregateId())), 50),
                json(p.get("before")), json(p.get("after")), truncate(str(p.get("ipAddress")), 45),
                Timestamp.from(event.occurredAt() != null ? event.occurredAt() : Instant.now()));
        return true;
    }

    private static String required(Map<String, Object> p, String key, int max) {
        String v = str(p.get(key));
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Event audit.log thiếu " + key);
        }
        return truncate(v.trim(), max);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("before/after không chuyển được sang JSON", ex);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
