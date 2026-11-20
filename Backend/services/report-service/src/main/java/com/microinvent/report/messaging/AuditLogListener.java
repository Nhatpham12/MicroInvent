package com.microinvent.report.messaging;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.report.config.AuditQueueConfig;
import com.microinvent.report.service.AuditLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Nhận event audit.log, chọn DB theo tenant của event rồi ghi audit_logs. Lỗi dữ liệu → DLQ, không requeue. */
@Component
public class AuditLogListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogListener.class);

    private final AuditLogWriter writer;

    public AuditLogListener(AuditLogWriter writer) {
        this.writer = writer;
    }

    private static boolean hasBusinessCause(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BusinessException) {
                return true;
            }
        }
        return false;
    }

    @RabbitListener(queues = AuditQueueConfig.AUDIT_QUEUE)
    public void onAuditLog(DomainEvent event) {
        if (event == null || !StringUtils.hasText(event.eventId()) || !StringUtils.hasText(event.tenantId())) {
            throw new AmqpRejectAndDontRequeueException("Event audit.log thiếu eventId hoặc tenantId");
        }
        TenantContext.set(event.tenantId());
        try {
            boolean written = writer.write(event);
            if (!written) {
                log.debug("Bỏ qua event trùng {}", event.eventId());
            }
        } catch (IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            // Tenant lạ (TENANT_NOT_FOUND, thường bị Spring bọc trong lỗi lấy kết nối) → không retry, vào DLQ.
            if (hasBusinessCause(ex)) {
                throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
            }
            throw ex;
        } finally {
            TenantContext.clear();
        }
    }
}
