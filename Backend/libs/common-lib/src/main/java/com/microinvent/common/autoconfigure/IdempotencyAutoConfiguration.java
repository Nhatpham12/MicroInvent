package com.microinvent.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.idempotency.IdempotencyService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Tạo IdempotencyService khi service có spring-boot-starter-data-redis. */
@AutoConfiguration(after = {RedisAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyService idempotencyService(StringRedisTemplate redis, ObjectMapper mapper) {
        return new IdempotencyService(redis, mapper);
    }
}
