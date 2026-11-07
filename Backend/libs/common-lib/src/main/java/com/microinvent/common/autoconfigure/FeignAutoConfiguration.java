package com.microinvent.common.autoconfigure;

import com.microinvent.common.feign.InternalFeignInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Interceptor Feign gắn X-Internal-Key, tenant, traceId, user cho mọi FeignClient. */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InternalFeignInterceptor internalFeignInterceptor(
            @Value("${microinvent.internal.api-key:${INTERNAL_API_KEY:change-me}}") String apiKey) {
        return new InternalFeignInterceptor(apiKey);
    }
}
