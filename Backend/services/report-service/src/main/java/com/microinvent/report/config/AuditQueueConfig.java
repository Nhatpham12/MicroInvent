package com.microinvent.report.config;

import com.microinvent.common.event.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hàng đợi nhật ký thao tác: {@value #AUDIT_QUEUE} nhận routing key {@value EventTypes#AUDIT_LOG} từ exchange chung.
 * Tin xử lý lỗi (không requeue) chuyển sang {@value #AUDIT_DLQ} để xem lại, không chặn hàng đợi chính.
 */
@Configuration
public class AuditQueueConfig {

    public static final String AUDIT_QUEUE = "report.audit-log";
    public static final String AUDIT_DLQ = "report.audit-log.dlq";

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(AUDIT_DLQ)
                .build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(auditQueue).to(eventsExchange).with(EventTypes.AUDIT_LOG);
    }
}
