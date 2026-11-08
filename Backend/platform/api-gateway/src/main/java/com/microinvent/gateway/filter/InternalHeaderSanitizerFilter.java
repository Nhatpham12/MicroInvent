package com.microinvent.gateway.filter;

import com.microinvent.common.web.HeaderNames;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Xóa các header nội bộ do client tự gửi lên để không giả mạo được người dùng.
 * JwtAuthenticationFilter (chạy ngay sau) gắn lại X-User-* từ claims của access token.
 */
@Component
public class InternalHeaderSanitizerFilter implements GlobalFilter, Ordered {

    static final List<String> INTERNAL_HEADERS = List.of(HeaderNames.USER_ID, HeaderNames.USER_NAME,
            HeaderNames.USER_ROLES, HeaderNames.WAREHOUSE_IDS, HeaderNames.ALL_WAREHOUSES, HeaderNames.INTERNAL_KEY);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> INTERNAL_HEADERS.forEach(h::remove)))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
