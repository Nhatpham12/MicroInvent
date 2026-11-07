package com.microinvent.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.security.JwtPrincipal;
import com.microinvent.common.security.JwtService;
import com.microinvent.common.web.ApiError;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.HeaderNames;
import com.microinvent.gateway.config.GatewaySecurityProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Xác thực access token (Authorization: Bearer ...; riêng /ws/** cho phép ?access_token=) rồi gắn header
 * X-User-Id, X-User-Name, X-User-Roles, X-Warehouse-Ids, X-All-Warehouses và X-Tenant-Id (lấy từ token,
 * ghi đè giá trị client gửi) cho service phía sau. Đường dẫn public đi qua không cần token.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_ATTR = "microinvent.userId";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final List<PathPattern> publicPatterns;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper,
            GatewaySecurityProperties props) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.publicPatterns = props.getPublicPaths().stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(request.getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        String token = extractToken(exchange);
        if (token == null) {
            return reject(exchange.getResponse(), ErrorCode.UNAUTHORIZED);
        }
        JwtPrincipal p;
        try {
            p = jwtService.parseAccessToken(token);
        } catch (BusinessException ex) {
            return reject(exchange.getResponse(), ex.getCode());
        }
        exchange.getAttributes().put(USER_ID_ATTR, p.userId());
        ServerWebExchange authenticated = exchange.mutate().request(r -> r.headers(h -> {
            h.set(HeaderNames.USER_ID, String.valueOf(p.userId()));
            h.set(HeaderNames.USER_NAME, p.username());
            h.set(HeaderNames.USER_ROLES, String.join(",", p.roles()));
            h.set(HeaderNames.WAREHOUSE_IDS,
                    p.warehouseIds().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
            h.set(HeaderNames.ALL_WAREHOUSES, String.valueOf(p.allWarehouses()));
            if (StringUtils.hasText(p.tenantId())) {
                h.set(HeaderNames.TENANT_ID, p.tenantId());
            }
        })).build();
        return chain.filter(authenticated);
    }

    boolean isPublic(PathContainer path) {
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String extractToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String token = auth.substring(BEARER.length()).trim();
            return token.isEmpty() ? null : token;
        }
        if (exchange.getRequest().getPath().value().startsWith("/ws/")) {
            return exchange.getRequest().getQueryParams().getFirst("access_token");
        }
        return null;
    }

    private Mono<Void> reject(ServerHttpResponse response, ErrorCode code) {
        response.setStatusCode(code.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                    ApiResponse.fail(new ApiError(code.name(), code.defaultMessage(), null)));
        } catch (JsonProcessingException ex) {
            body = ("{\"success\":false,\"error\":{\"code\":\"" + code.name() + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
