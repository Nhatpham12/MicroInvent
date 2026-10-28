package com.microinvent.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event gửi qua RabbitMQ (exchange {@link EventTypes#EXCHANGE}); luôn mang tenantId. */
public record DomainEvent(String eventId, String eventType, String tenantId, String aggregateId, Instant occurredAt,
        Map<String, Object> payload) {

    public static DomainEvent of(String eventType, String tenantId, Object aggregateId, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID().toString(), eventType, tenantId, String.valueOf(aggregateId),
                Instant.now(), payload);
    }
}
