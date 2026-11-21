package com.microinvent.report.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.retry.support.RetryTemplate;

class AuditRetryConfigTest {

    private RetryTemplate listenerTemplate() {
        RabbitProperties props = new RabbitProperties();
        props.getListener().getSimple().getRetry().setMaxAttempts(3);
        RetryTemplate template = new RetryTemplate();
        new AuditRetryConfig().noRetryForRejectedMessages(props)
                .customize(RabbitRetryTemplateCustomizer.Target.LISTENER, template);
        return template;
    }

    @Test
    void rejectedMessageIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> listenerTemplate().execute(ctx -> {
            calls.incrementAndGet();
            throw new ListenerExecutionFailedException("x", new AmqpRejectAndDontRequeueException("thiếu action"));
        })).isInstanceOf(ListenerExecutionFailedException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void transientErrorIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> listenerTemplate().execute(ctx -> {
            calls.incrementAndGet();
            throw new ListenerExecutionFailedException("x", new IllegalStateException("DB down"));
        })).isInstanceOf(ListenerExecutionFailedException.class);
        assertThat(calls).hasValue(3);
    }
}
