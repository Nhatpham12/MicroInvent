package com.microinvent.gateway.config;

import com.microinvent.gateway.filter.JwtAuthenticationFilter;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /** Giới hạn theo user đã đăng nhập; request chưa đăng nhập (login...) giới hạn theo IP. */
    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            Object userId = exchange.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getAddress).map(a -> a.getHostAddress()).orElse("unknown");
            return Mono.just("ip:" + ip);
        };
    }
}
