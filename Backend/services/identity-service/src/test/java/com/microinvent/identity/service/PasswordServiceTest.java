package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.PasswordOtp;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.repository.PasswordOtpRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.ResetTokenResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private UserRepository users;
    private PasswordOtpRepository otps;
    private RefreshTokenRepository refreshTokens;
    private ApplicationEventPublisher events;
    private PasswordService service;
    private User user;
    /** OTP rõ mà service gửi qua mail (bắt từ mock). */
    private final AtomicReference<String> sentOtp = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        otps = mock(PasswordOtpRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PasswordService(users, otps, refreshTokens, ENCODER, events, CLOCK);

        user = new User();
        user.setId(7);
        user.setUsername("thukho");
        user.setEmail("thukho@tigernet.vn");
        user.setFullName("Thủ kho");
        user.setPasswordHash(ENCODER.encode("Old@1234"));
        when(users.findByLogin("thukho@tigernet.vn")).thenReturn(Optional.of(user));
        when(users.findById(7)).thenReturn(Optional.of(user));
        when(otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(7)).thenReturn(Optional.empty());
        when(otps.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            sentOtp.set(((OtpMailRequested) inv.getArgument(0)).otp());
            return null;
        }).when(events).publishEvent(any(OtpMailRequested.class));
    }

    /** Gửi OTP rồi để repository trả về đúng bản ghi vừa lưu. */
    private PasswordOtp requestOtp() {
        service.forgotPassword("thukho@tigernet.vn");
        ArgumentCaptor<PasswordOtp> saved = ArgumentCaptor.forClass(PasswordOtp.class);
        verify(otps).save(saved.capture());
        when(otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(7)).thenReturn(Optional.of(saved.getValue()));
        when(otps.findFirstForUpdateByUserIdOrderByCreatedAtDescIdDesc(7)).thenReturn(Optional.of(saved.getValue()));
        return saved.getValue();
    }

    @Test
    void forgotPasswordStoresHashedOtpAndSendsMail() {
        PasswordOtp otp = requestOtp();

        assertThat(sentOtp.get()).matches("\\d{6}");
        assertThat(otp.getOtpHash()).isNotEqualTo(sentOtp.get());
        assertThat(ENCODER.matches(sentOtp.get(), otp.getOtpHash())).isTrue();
        assertThat(otp.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        verify(otps).invalidateAll(7, NOW);
        ArgumentCaptor<OtpMailRequested> mail = ArgumentCaptor.forClass(OtpMailRequested.class);
        verify(events).publishEvent(mail.capture());
        assertThat(mail.getValue().email()).isEqualTo("thukho@tigernet.vn");
        assertThat(mail.getValue().toString()).doesNotContain(sentOtp.get());
    }

    @Test
    void forgotPasswordForUnknownOrDisabledEmailIsSilent() {
        service.forgotPassword("khong-ton-tai@x.vn");
        user.setActive(false);
        service.forgotPassword("thukho@tigernet.vn");

        verify(otps, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void resendWithinSixtySecondsIsIgnored() {
        PasswordOtp last = new PasswordOtp();
        last.setCreatedAt(NOW.minusSeconds(30));
        when(otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(7)).thenReturn(Optional.of(last));

        service.forgotPassword("thukho@tigernet.vn");

        verify(otps, never()).save(any());
    }

    @Test
    void unknownAndKnownEmailBothRunBcryptOnce() {
        PasswordEncoder counting = mock(PasswordEncoder.class);
        when(counting.encode(any())).thenReturn("hash");
        PasswordService s = new PasswordService(users, otps, refreshTokens, counting, events, CLOCK);

        s.forgotPassword("khong-ton-tai@x.vn");
        verify(counting, times(1)).encode(any());
        s.forgotPassword("thukho@tigernet.vn");
        verify(counting, times(2)).encode(any());
    }

    @Test
    void verifyOtpReadsOtpWithRowLock() {
        requestOtp();
        String wrong = sentOtp.get().equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> service.verifyOtp("thukho@tigernet.vn", wrong))
                .isInstanceOf(BusinessException.class);
        verify(otps).findFirstForUpdateByUserIdOrderByCreatedAtDescIdDesc(7);
    }

    @Test
    void reusedResetTokenIsRejected() {
        PasswordOtp otp = requestOtp();
        ResetTokenResponse res = service.verifyOtp("thukho@tigernet.vn", sentOtp.get());
        // Lần 1: còn token. Lần 2 (request song song chờ khóa rồi đọc lại): token đã bị xóa → không tìm thấy.
        when(otps.findForUpdateByResetTokenHash(otp.getResetTokenHash()))
                .thenReturn(Optional.of(otp)).thenReturn(Optional.empty());
        service.resetPassword(res.resetToken(), "New@12345");
        assertThat(otp.getResetTokenHash()).isNull();

        assertThatThrownBy(() -> service.resetPassword(res.resetToken(), "Other@12345"))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID);
    }

    @Test
    void correctOtpIssuesOneTimeResetTokenThenResetsPassword() {
        PasswordOtp otp = requestOtp();

        ResetTokenResponse res = service.verifyOtp("thukho@tigernet.vn", sentOtp.get());
        assertThat(res.resetToken()).isNotBlank();
        assertThat(otp.getUsedAt()).isEqualTo(NOW);
        assertThat(otp.getResetTokenHash()).isEqualTo(TokenHasher.sha256(res.resetToken()));

        when(otps.findForUpdateByResetTokenHash(otp.getResetTokenHash())).thenReturn(Optional.of(otp));
        user.setLockedUntil(NOW.plusMinutes(3));
        service.resetPassword(res.resetToken(), "New@12345");

        assertThat(ENCODER.matches("New@12345", user.getPasswordHash())).isTrue();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(otp.getResetTokenHash()).isNull();
        verify(refreshTokens).revokeAllByUserId(7, NOW);
    }

    @Test
    void otpIsUsableOnlyOnce() {
        requestOtp();
        service.verifyOtp("thukho@tigernet.vn", sentOtp.get());
        assertThatThrownBy(() -> service.verifyOtp("thukho@tigernet.vn", sentOtp.get()))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    void fiveWrongOtpsInvalidateTheCode() {
        PasswordOtp otp = requestOtp();
        String wrong = sentOtp.get().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < PasswordService.OTP_MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verifyOtp("thukho@tigernet.vn", wrong))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(otp.getUsedAt()).isNotNull();
        // Đã bị hủy: nhập đúng cũng không được nữa
        assertThatThrownBy(() -> service.verifyOtp("thukho@tigernet.vn", sentOtp.get()))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    void expiredOtpIsRejected() {
        PasswordOtp otp = requestOtp();
        otp.setExpiresAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.verifyOtp("thukho@tigernet.vn", sentOtp.get()))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    void expiredResetTokenIsRejected() {
        PasswordOtp otp = new PasswordOtp();
        otp.setUserId(7);
        otp.setResetTokenHash(TokenHasher.sha256("tok"));
        otp.setResetExpiresAt(NOW.minusSeconds(1));
        when(otps.findForUpdateByResetTokenHash(eq(TokenHasher.sha256("tok")))).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword("tok", "New@12345"))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.RESET_TOKEN_INVALID);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword(7, "sai", "New@12345"))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.WRONG_PASSWORD);
        assertThatThrownBy(() -> service.changePassword(7, "Old@1234", "Old@1234"))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.BAD_REQUEST);

        service.changePassword(7, "Old@1234", "New@12345");
        assertThat(ENCODER.matches("New@12345", user.getPasswordHash())).isTrue();
        verify(refreshTokens).revokeAllByUserId(7, NOW);
    }
}
