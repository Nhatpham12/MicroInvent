package com.microinvent.common.messaging;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.event.DomainEvent;
import com.microinvent.common.event.EventTypes;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Gửi DomainEvent lên topic exchange {@link EventTypes#EXCHANGE} với routing key = eventType. */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DomainEvent event) {
        log.debug("Publish {} tenant={} aggregate={}", event.eventType(), event.tenantId(), event.aggregateId());
        rabbitTemplate.convertAndSend(EventTypes.EXCHANGE, event.eventType(), event, message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setHeader("x-tenant-id", event.tenantId());
            return message;
        });
    }

    /** Tạo event với tenant của request hiện tại rồi gửi. */
    public DomainEvent publish(String eventType, Object aggregateId, Map<String, Object> payload) {
        DomainEvent event = DomainEvent.of(eventType, TenantContext.get(), aggregateId, payload);
        publish(event);
        return event;
    }
}
