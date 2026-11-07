package com.microinvent.common.security;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.util.Assert;

/**
 * Ký và xác thực access token bằng HMAC-SHA (HS256/384/512 tự chọn theo độ dài secret).
 * Không phụ thuộc servlet/webflux nên dùng chung cho identity-service (ký) và api-gateway (xác thực).
 */
public class JwtService {

    public static final String CLAIM_USERNAME = "usr";
    public static final String CLAIM_TENANT = "tid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_WAREHOUSES = "wh";
    public static final String CLAIM_ALL_WAREHOUSES = "allWh";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";

    private final JwtProperties props;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    public JwtService(JwtProperties props, Clock clock) {
        Assert.hasText(props.getSecret(), "microinvent.jwt.secret is required");
        byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(bytes.length >= 32, "microinvent.jwt.secret must be at least 32 bytes (256 bit)");
        this.props = props;
        this.key = Keys.hmacShaKeyFor(bytes);
        this.clock = clock;
    }

    public String issueAccessToken(JwtPrincipal p) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.getIssuer())
                .subject(String.valueOf(p.userId()))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_USERNAME, p.username())
                .claim(CLAIM_TENANT, p.tenantId())
                .claim(CLAIM_ROLES, List.copyOf(p.roles()))
                .claim(CLAIM_WAREHOUSES, p.warehouseIds().stream().sorted().toList())
                .claim(CLAIM_ALL_WAREHOUSES, p.allWarehouses())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** @throws BusinessException TOKEN_INVALID nếu sai chữ ký, hết hạn hoặc không phải access token */
    public JwtPrincipal parseAccessToken(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getIssuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_ACCESS.equals(c.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
            return new JwtPrincipal(
                    Long.valueOf(c.getSubject()),
                    c.get(CLAIM_USERNAME, String.class),
                    c.get(CLAIM_TENANT, String.class),
                    toStrings(c.get(CLAIM_ROLES, Collection.class)),
                    toLongs(c.get(CLAIM_WAREHOUSES, Collection.class)),
                    Boolean.TRUE.equals(c.get(CLAIM_ALL_WAREHOUSES, Boolean.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public long accessTokenTtlSeconds() {
        return props.getAccessTokenTtl().toSeconds();
    }

    private static Set<String> toStrings(Collection<?> values) {
        return values == null ? Set.of() : values.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    private static Set<Long> toLongs(Collection<?> values) {
        return values == null ? Set.of()
                : values.stream().map(v -> ((Number) v).longValue()).collect(Collectors.toSet());
    }
}
