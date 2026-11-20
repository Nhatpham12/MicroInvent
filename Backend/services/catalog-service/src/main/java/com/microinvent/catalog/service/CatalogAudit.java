package com.microinvent.catalog.service;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.event.AuditPayload;
import com.microinvent.common.event.EventTypes;
import com.microinvent.tenant.outbox.OutboxWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi nhật ký thao tác của catalog-service (GAP-01): event {@value EventTypes#AUDIT_LOG} ghi vào outbox
 * trong cùng transaction với thao tác, OutboxRelay đẩy lên RabbitMQ, report-service ghi audit_logs.
 * <p>
 * Không đưa STK / CCCD vào before/after – chỉ ghi các trường nhận diện (mã, tên...).
 */
@Component
public class CatalogAudit {

    static final String SERVICE = "catalog-service";

    private final OutboxWriter outbox;

    public CatalogAudit(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * @param action CREATE / UPDATE / DELETE / IMPORT / UPLOAD_IMAGE
     * @param entity PRODUCT / PRODUCT_GROUP / SUPPLIER / CUSTOMER
     */
    public void record(String action, String entity, Integer entityId, Object before, Object after) {
        UserContext user = UserContextHolder.get();
        AuditPayload payload = new AuditPayload(user.userId(), user.username(), user.currentWarehouseId(), SERVICE,
                action, entity, entityId == null ? null : String.valueOf(entityId), before, after, clientIp());
        outbox.append(EventTypes.AUDIT_LOG, entity, entityId, payload.toMap());
    }

    /** Ảnh chụp các trường không nhạy cảm (bỏ qua giá trị null). */
    static Map<String, Object> snapshot(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return m;
    }

    /** IP client: gateway gửi X-Forwarded-For (chỉ tin từ proxy nội bộ); không có request thì null. */
    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
