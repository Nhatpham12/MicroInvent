package com.microinvent.report.config;

import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Retry của listener chỉ áp cho lỗi tạm thời (DB mất kết nối...). Event hỏng dữ liệu (thiếu action, tenant lạ)
 * listener ném {@link AmqpRejectAndDontRequeueException} → vào DLQ ngay, không retry 3 lần chặn hàng đợi.
 */
@Configuration
public class AuditRetryConfig {

    @Bean
    public RabbitRetryTemplateCustomizer noRetryForRejectedMessages(RabbitProperties props) {
        int maxAttempts = props.getListener().getSimple().getRetry().getMaxAttempts();
        return (target, template) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                template.setRetryPolicy(new SimpleRetryPolicy(maxAttempts,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true));
            }
        };
    }
}
