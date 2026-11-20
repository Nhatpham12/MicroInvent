package com.microinvent.common.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Nội dung chuẩn của event {@link EventTypes#AUDIT_LOG} (report-service ghi vào audit_logs).
 * Service nào cần ghi nhật ký thì: {@code eventPublisher.publish(EventTypes.AUDIT_LOG, entityId, payload.toMap())}.
 *
 * @param action CREATE, UPDATE, DELETE, CONFIRM, APPROVE, LOGIN...
 * @param entity tên đối tượng: PRODUCT, SUPPLIER, RECEIPT...
 * @param before / after dữ liệu trước / sau (map hoặc DTO), có thể null
 */
public record AuditPayload(Long userId, String username, Long warehouseId, String service, String action,
        String entity, String entityId, Object before, Object after, String ipAddress) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("username", username);
        m.put("warehouseId", warehouseId);
        m.put("service", service);
        m.put("action", action);
        m.put("entity", entity);
        m.put("entityId", entityId);
        m.put("before", before);
        m.put("after", after);
        m.put("ipAddress", ipAddress);
        return m;
    }
}
