package com.microinvent.tenant.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.messaging.EventPublisher;
import com.microinvent.tenant.starter.TenantDataSourceAutoConfiguration;
import com.microinvent.tenant.starter.TenantProperties;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bật transactional outbox cho service: {@code microinvent.outbox.enabled=true} (mặc định tắt vì service phải có
 * bảng outbox_events trong migration). Cấu hình thêm: {@code poll-interval} (2s), {@code batch-size} (100),
 * {@code max-retries} (10).
 */
@AutoConfiguration(after = {TenantDataSourceAutoConfiguration.class, RabbitAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class})
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.core.RabbitTemplate",
        "com.fasterxml.jackson.databind.ObjectMapper"})
@ConditionalOnProperty(prefix = "microinvent.outbox", name = "enabled", havingValue = "true")
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(DataSource dataSource, ObjectMapper objectMapper,
            ObjectProvider<Clock> clock) {
        return new OutboxWriter(new JdbcTemplate(dataSource), objectMapper, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(DataSource dataSource, PlatformTransactionManager txManager,
            EventPublisher publisher, ObjectMapper objectMapper, TenantProperties tenants,
            @Value("${microinvent.outbox.batch-size:100}") int batchSize,
            @Value("${microinvent.outbox.max-retries:10}") int maxRetries, ObjectProvider<Clock> clock) {
        Set<String> all = new LinkedHashSet<>(tenants.getTenants());
        all.add(tenants.getDefaultTenant());
        return new OutboxRelay(new JdbcTemplate(dataSource), new TransactionTemplate(txManager), publisher,
                objectMapper, all, batchSize, maxRetries, clock.getIfAvailable(Clock::systemUTC));
    }
}
