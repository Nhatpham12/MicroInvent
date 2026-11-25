package com.microinvent.sales.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** Cấu hình riêng cho InventoryClient / CatalogClient của sales-service (không đặt @Configuration để không áp cho client khác). */
public class DownstreamFeignConfig {

    @Bean
    ErrorDecoder downstreamErrorDecoder(ObjectMapper mapper) {
        return new DownstreamErrorDecoder(mapper);
    }
}
