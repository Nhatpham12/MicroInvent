package com.microinvent.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Xóa các header nội bộ do client tự gửi lên để không giả mạo được người dùng.
 * TODO(identity): thêm JwtAuthFilter xác thực token rồi gắn lại X-User-* từ claims.
 */
@Component
public class InternalHeaderSanitizerFilter implements GlobalFilter, Ordered {

    private static final List<String> INTERNAL_HEADERS = List.of("X-User-Id", "X-User-Name", "X-User-Roles",
            "X-Warehouse-Ids", "X-All-Warehouses", "X-Internal-Key");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> INTERNAL_HEADERS.forEach(h::remove)))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
