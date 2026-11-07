package com.microinvent.gateway.filter;

import com.microinvent.common.web.HeaderNames;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Gắn X-Trace-Id cho mỗi request (giữ nguyên nếu client đã gửi) và trả lại trong response để dò log. */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HeaderNames.TRACE_ID);
        if (!StringUtils.hasText(traceId) || traceId.length() > 64) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String id = traceId;
        // Ghi đè khi sắp gửi response để không bị trùng với header service phía sau trả về.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HeaderNames.TRACE_ID, id);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(r -> r.headers(h -> h.set(HeaderNames.TRACE_ID, id))).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
