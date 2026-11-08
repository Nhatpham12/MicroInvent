package com.microinvent.identity.service;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.security.JwtPrincipal;
import com.microinvent.common.security.JwtProperties;
import com.microinvent.common.security.JwtService;
import com.microinvent.identity.domain.LoginHistory;
import com.microinvent.identity.domain.RefreshToken;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.domain.UserWarehouse;
import com.microinvent.identity.repository.LoginHistoryRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.LoginRequest;
import com.microinvent.identity.web.dto.TokenResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đăng nhập / refresh / đăng xuất (SRS 3.1.1).
 * <ul>
 * <li>Sai mật khẩu MAX_FAILED_LOGIN (mặc định 5) lần liên tiếp → khóa LOCK_MINUTES (mặc định 5) phút.</li>
 * <li>Mọi lần đăng nhập (thành công/thất bại) ghi login_histories.</li>
 * <li>Refresh token xoay vòng (rotate); dùng lại token đã thu hồi → thu hồi toàn bộ phiên của user.</li>
 * </ul>
 * noRollbackFor để vẫn lưu số lần sai / lịch sử / thu hồi token khi ném BusinessException.
 */
@Service
@Transactional(noRollbackFor = BusinessException.class)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginHistoryRepository loginHistories;
    private final SettingService settings;
    private final UserScopeService scopeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final String defaultTenant;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
            LoginHistoryRepository loginHistories, SettingService settings, UserScopeService scopeService,
            PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties, Clock clock,
            @Value("${microinvent.tenant.default-tenant:tigernet}") String defaultTenant) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.loginHistories = loginHistories;
        this.settings = settings;
        this.scopeService = scopeService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.defaultTenant = defaultTenant;
    }

    public TokenResponse login(LoginRequest req, ClientInfo client) {
        LocalDateTime now = LocalDateTime.now(clock);
        String login = req.username().trim();
        Optional<User> found = users.findByLogin(login);
        if (found.isEmpty()) {
            // Vẫn chạy bcrypt để không lộ thông tin "user có tồn tại hay không" qua thời gian phản hồi.
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            recordLogin(null, login, false, client, now);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!user.isActive()) {
            recordLogin(user.getId(), user.getUsername(), false, client, now);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.isLocked(now)) {
            recordLogin(user.getId(), user.getUsername(), false, client, now);
            throw locked(user);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            onWrongPassword(user, client, now);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        recordLogin(user.getId(), user.getUsername(), true, client, now);
        return issueTokens(scopeService.loadWithScope(user.getId()), client, now);
    }

    public TokenResponse refresh(String rawRefreshToken, ClientInfo client) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken current = refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (current.getRevokedAt() != null) {
            log.warn("Refresh token đã thu hồi bị dùng lại, thu hồi mọi phiên của user {}", current.getUserId());
            refreshTokens.revokeAllByUserId(current.getUserId(), now);
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!current.isActive(now)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        current.setRevokedAt(now);
        User user = scopeService.loadWithScope(current.getUserId());
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return issueTokens(user, client, now);
    }

    /** Thu hồi refresh token. Chỉ thu hồi token thuộc chính user đang đăng nhập (nếu biết userId). */
    public void logout(String rawRefreshToken, Long currentUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .filter(t -> currentUserId == null || t.getUserId().longValue() == currentUserId)
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(now));
    }

    private void onWrongPassword(User user, ClientInfo client, LocalDateTime now) {
        int maxFailed = settings.getInt(SettingService.MAX_FAILED_LOGIN, 5);
        int lockMinutes = settings.getInt(SettingService.LOCK_MINUTES, 5);
        int failed = user.getFailedLoginCount() + 1;
        recordLogin(user.getId(), user.getUsername(), false, client, now);
        if (failed >= maxFailed) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(now.plusMinutes(lockMinutes));
            throw locked(user);
        }
        user.setFailedLoginCount(failed);
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                Map.of("remainingAttempts", maxFailed - failed));
    }

    private static BusinessException locked(User user) {
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                "Tài khoản bị khóa tạm thời do đăng nhập sai nhiều lần",
                Map.of("lockedUntil", user.getLockedUntil().truncatedTo(ChronoUnit.SECONDS).toString()));
    }

    private TokenResponse issueTokens(User user, ClientInfo client, LocalDateTime now) {
        Set<Long> warehouseIds = user.getWarehouses().stream()
                .map(UserWarehouse::getWarehouseId).map(Integer::longValue).collect(Collectors.toSet());
        String tenant = TenantContext.get() != null ? TenantContext.get() : defaultTenant;
        String accessToken = jwtService.issueAccessToken(new JwtPrincipal(user.getId().longValue(),
                user.getUsername(), tenant, Set.of(user.getRole().getCode()), warehouseIds,
                user.getRole().isAllWarehouses()));

        String rawRefresh = TokenHasher.newToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(TokenHasher.sha256(rawRefresh));
        rt.setUserAgent(truncate(client.userAgent(), 255));
        rt.setIpAddress(truncate(client.ip(), 45));
        rt.setExpiresAt(now.plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokens.save(rt);

        return TokenResponse.bearer(accessToken, rawRefresh, jwtService.accessTokenTtlSeconds(),
                UserScopeService.toMe(user));
    }

    private void recordLogin(Integer userId, String username, boolean success, ClientInfo client,
            LocalDateTime now) {
        LoginHistory h = new LoginHistory();
        h.setUserId(userId);
        h.setUsername(truncate(username, 50));
        h.setSuccess(success);
        h.setIpAddress(truncate(client.ip(), 45));
        h.setUserAgent(truncate(client.userAgent(), 255));
        h.setCreatedAt(now);
        loginHistories.save(h);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** bcrypt của một chuỗi ngẫu nhiên, chỉ dùng để cân bằng thời gian khi user không tồn tại. */
    private static final String DUMMY_HASH = "$2a$10$qKMHjviQUSibJZgxUyU0heG/6LKB1k/TCtNfZXxXP4zLRGvGC24Sm";

    /** IP + User-Agent của client (gateway chuyển qua X-Forwarded-For). */
    public record ClientInfo(String ip, String userAgent) {
    }
}
