package com.microinvent.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.security.JwtPrincipal;
import com.microinvent.common.security.JwtProperties;
import com.microinvent.common.security.JwtService;
import com.microinvent.common.web.HeaderNames;
import com.microinvent.gateway.config.GatewaySecurityProperties;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private final JwtService jwt;
    private final JwtAuthenticationFilter filter;
    private final AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange.getRequest());
        return Mono.empty();
    };

    JwtAuthenticationFilterTest() {
        JwtProperties props = new JwtProperties();
        props.setSecret("gateway-test-secret-gateway-test-secret-12");
        jwt = new JwtService(props);
        GatewaySecurityProperties security = new GatewaySecurityProperties();
        security.setPublicPaths(List.of("/api/v1/auth/login", "/actuator/health/**"));
        filter = new JwtAuthenticationFilter(jwt, new ObjectMapper(), security);
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void publicPathPassesWithoutToken() {
        run(MockServerHttpRequest.post("/api/v1/auth/login"));
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void protectedPathWithoutTokenIs401() {
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/api/v1/products"));
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("UNAUTHORIZED");
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void invalidTokenIs401() {
        MockServerWebExchange ex = run(MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("TOKEN_INVALID");
    }

    @Test
    void validTokenAddsUserHeadersAndOverridesTenant() {
        String token = jwt.issueAccessToken(
                new JwtPrincipal(5L, "thukho", "tigernet", Set.of("STOREKEEPER"), Set.of(2L, 1L), false));

        MockServerWebExchange ex = run(MockServerHttpRequest.get("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderNames.TENANT_ID, "tenant-khac"));

        HttpHeaders h = forwarded.get().getHeaders();
        assertThat(h.getFirst(HeaderNames.USER_ID)).isEqualTo("5");
        assertThat(h.getFirst(HeaderNames.USER_NAME)).isEqualTo("thukho");
        assertThat(h.getFirst(HeaderNames.USER_ROLES)).isEqualTo("STOREKEEPER");
        assertThat(h.getFirst(HeaderNames.WAREHOUSE_IDS)).isEqualTo("1,2");
        assertThat(h.getFirst(HeaderNames.ALL_WAREHOUSES)).isEqualTo("false");
        assertThat(h.getFirst(HeaderNames.TENANT_ID)).isEqualTo("tigernet");
        assertThat((Object) ex.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR)).isEqualTo(5L);
    }

    @Test
    void websocketAcceptsTokenFromQuery() {
        String token = jwt.issueAccessToken(new JwtPrincipal(1L, "admin", "tigernet", Set.of("ADMIN"), null, true));
        run(MockServerHttpRequest.get("/ws/notifications?access_token=" + token));
        assertThat(forwarded.get().getHeaders().getFirst(HeaderNames.USER_ID)).isEqualTo("1");
    }
}
