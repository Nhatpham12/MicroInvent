package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.security.JwtPrincipal;
import com.microinvent.common.security.JwtProperties;
import com.microinvent.common.security.JwtService;
import com.microinvent.identity.domain.RefreshToken;
import com.microinvent.identity.domain.Role;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.repository.LoginHistoryRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.service.AuthService.ClientInfo;
import com.microinvent.identity.web.dto.LoginRequest;
import com.microinvent.identity.web.dto.TokenResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T02:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private LoginHistoryRepository histories;
    private SettingService settings;
    private UserScopeService scope;
    private JwtService jwt;
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        histories = mock(LoginHistoryRepository.class);
        settings = mock(SettingService.class);
        scope = mock(UserScopeService.class);
        when(settings.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JwtProperties props = new JwtProperties();
        props.setSecret("unit-test-secret-unit-test-secret-1234567");
        jwt = new JwtService(props);
        service = new AuthService(users, refreshTokens, histories, settings, scope, ENCODER, jwt, props, CLOCK,
                "tigernet");

        Role role = new Role();
        role.setCode("STOREKEEPER");
        role.setName("Thủ kho");
        user = new User();
        user.setId(2);
        user.setUsername("thukho");
        user.setEmail("thukho@tigernet.vn");
        user.setFullName("Thủ kho");
        user.setPasswordHash(ENCODER.encode("Thukho@123"));
        user.setRole(role);
        when(users.findByLogin("thukho")).thenReturn(Optional.of(user));
        when(scope.loadWithScope(2)).thenReturn(user);
    }

    private BusinessException loginFails(String password) {
        try {
            service.login(new LoginRequest("thukho", password), CLIENT);
        } catch (BusinessException ex) {
            return ex;
        }
        throw new AssertionError("login phải thất bại");
    }

    @Test
    void successfulLoginIssuesTokensAndResetsCounter() {
        user.setFailedLoginCount(3);

        TokenResponse res = service.login(new LoginRequest("thukho", "Thukho@123"), CLIENT);

        JwtPrincipal p = jwt.parseAccessToken(res.accessToken());
        assertThat(p.userId()).isEqualTo(2L);
        assertThat(p.tenantId()).isEqualTo("tigernet");
        assertThat(p.roles()).containsExactly("STOREKEEPER");
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        verify(histories).save(any());
    }

    @Test
    void fiveWrongPasswordsLockAccountForFiveMinutes() {
        for (int i = 1; i <= 4; i++) {
            assertThat(loginFails("sai").getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(user.getFailedLoginCount()).isEqualTo(4);

        assertThat(loginFails("sai").getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusMinutes(5));

        // Đang khóa thì mật khẩu đúng cũng bị từ chối
        assertThat(loginFails("Thukho@123").getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        verify(histories, times(6)).save(any());
    }

    @Test
    void lockExpiresAfterLockTime() {
        user.setLockedUntil(NOW.minusSeconds(1));
        TokenResponse res = service.login(new LoginRequest("thukho", "Thukho@123"), CLIENT);
        assertThat(res.accessToken()).isNotBlank();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void disabledAccountCannotLogin() {
        user.setActive(false);
        assertThat(loginFails("Thukho@123").getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void unknownUserGetsGenericError() {
        assertThatThrownBy(() -> service.login(new LoginRequest("khongco", "x"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void refreshRotatesToken() {
        RefreshToken old = new RefreshToken();
        old.setUserId(2);
        old.setExpiresAt(NOW.plusDays(1));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("old"))).thenReturn(Optional.of(old));

        TokenResponse res = service.refresh("old", CLIENT);

        assertThat(old.getRevokedAt()).isEqualTo(NOW);
        assertThat(res.refreshToken()).isNotEqualTo("old");
    }

    @Test
    void reusingRevokedRefreshTokenRevokesAllSessions() {
        RefreshToken old = new RefreshToken();
        old.setUserId(2);
        old.setExpiresAt(NOW.plusDays(1));
        old.setRevokedAt(NOW.minusMinutes(1));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("old"))).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.refresh("old", CLIENT)).isInstanceOf(BusinessException.class);
        verify(refreshTokens).revokeAllByUserId(2, NOW);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        RefreshToken old = new RefreshToken();
        old.setUserId(2);
        old.setExpiresAt(NOW.minusSeconds(1));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("old"))).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.refresh("old", CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void logoutOnlyRevokesOwnToken() {
        RefreshToken t = new RefreshToken();
        t.setUserId(2);
        t.setExpiresAt(NOW.plusDays(1));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("rt"))).thenReturn(Optional.of(t));

        service.logout("rt", 99L);
        assertThat(t.getRevokedAt()).isNull();

        service.logout("rt", 2L);
        assertThat(t.getRevokedAt()).isEqualTo(NOW);
    }
}
