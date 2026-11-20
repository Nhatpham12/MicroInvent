package com.microinvent.procurement.messaging;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.messaging.EventPublisher;
import com.microinvent.procurement.domain.OutboxEvent;
import com.microinvent.procurement.repository.OutboxEventRepository;
import com.microinvent.tenant.starter.TenantProperties;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Đẩy outbox_events PENDING lên RabbitMQ theo từng tenant (job chạy ngoài HTTP request nên tự set TenantContext).
 * Mất kết nối RabbitMQ: dừng lượt này, giữ nguyên PENDING. Lỗi khác: tăng retry_count, quá 10 lần → FAILED.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    static final int BATCH_SIZE = 50;
    static final int MAX_RETRY = 10;

    private final OutboxEventRepository outbox;
    private final EventPublisher publisher;
    private final TenantProperties tenants;
    private final TransactionTemplate tx;

    public OutboxPublisher(OutboxEventRepository outbox, EventPublisher publisher, TenantProperties tenants,
            TransactionTemplate tx) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.tenants = tenants;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${microinvent.outbox.publish-interval-ms:5000}")
    public void publishAllTenants() {
        Set<String> all = new LinkedHashSet<>(tenants.getTenants());
        all.add(tenants.getDefaultTenant());
        for (String tenant : all) {
            TenantContext.set(tenant);
            try {
                tx.executeWithoutResult(s -> publishPending(tenant));
            } catch (RuntimeException ex) {
                log.warn("Outbox tenant {}: {}", tenant, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    void publishPending(String tenant) {
        for (OutboxEvent e : outbox.findByStatusOrderByIdAsc(OutboxEvent.Status.PENDING, PageRequest.of(0, BATCH_SIZE))) {
            try {
                publisher.publish(new DomainEvent(e.getEventId(), e.getEventType(), tenant,
                        String.valueOf(e.getAggregateId()), e.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                        e.getPayload()));
                e.setStatus(OutboxEvent.Status.PUBLISHED);
                e.setPublishedAt(LocalDateTime.now());
            } catch (AmqpConnectException ex) {
                log.warn("RabbitMQ không kết nối được, thử lại lượt sau");
                return;
            } catch (RuntimeException ex) {
                e.setRetryCount(e.getRetryCount() + 1);
                e.setLastError(ex.toString());
                if (e.getRetryCount() >= MAX_RETRY) {
                    e.setStatus(OutboxEvent.Status.FAILED);
                }
            }
        }
    }
}
