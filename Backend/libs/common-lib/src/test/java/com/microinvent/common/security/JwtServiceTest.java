package com.microinvent.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    private static JwtProperties props(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setAccessTokenTtl(Duration.ofMinutes(15));
        return p;
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtService jwt = new JwtService(props(SECRET));
        String token = jwt.issueAccessToken(
                new JwtPrincipal(7L, "thukho", "tigernet", Set.of("STOREKEEPER"), Set.of(1L, 3L), false));

        JwtPrincipal p = jwt.parseAccessToken(token);

        assertThat(p.userId()).isEqualTo(7L);
        assertThat(p.username()).isEqualTo("thukho");
        assertThat(p.tenantId()).isEqualTo("tigernet");
        assertThat(p.roles()).containsExactly("STOREKEEPER");
        assertThat(p.warehouseIds()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(p.allWarehouses()).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        Instant issued = Instant.parse("2026-11-01T00:00:00Z");
        JwtService issuer = new JwtService(props(SECRET), Clock.fixed(issued, ZoneOffset.UTC));
        String token = issuer.issueAccessToken(new JwtPrincipal(1L, "admin", "tigernet", Set.of("ADMIN"), null, true));

        JwtService later = new JwtService(props(SECRET), Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String token = new JwtService(props("another-secret-another-secret-another-1234"))
                .issueAccessToken(new JwtPrincipal(1L, "admin", "tigernet", Set.of("ADMIN"), null, true));

        assertThatThrownBy(() -> new JwtService(props(SECRET)).parseAccessToken(token))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> new JwtService(props(SECRET)).parseAccessToken("abc.def.ghi"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(props("too-short"))).isInstanceOf(IllegalArgumentException.class);
    }
}
