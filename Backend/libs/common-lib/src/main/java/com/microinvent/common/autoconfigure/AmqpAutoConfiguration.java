package com.microinvent.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.event.EventTypes;
import com.microinvent.common.messaging.EventPublisher;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình RabbitMQ dùng chung: JSON converter (Jackson, hỗ trợ Instant), topic exchange
 * {@value EventTypes#EXCHANGE} và {@link EventPublisher}. Queue/binding do từng service consumer tự khai báo.
 */
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass({RabbitTemplate.class, ObjectMapper.class})
public class AmqpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "eventsExchange")
    public TopicExchange eventsExchange() {
        return new TopicExchange(EventTypes.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate) {
        return new EventPublisher(rabbitTemplate);
    }
}
