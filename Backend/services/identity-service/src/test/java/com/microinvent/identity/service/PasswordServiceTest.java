package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.mail.MailSendException;
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
    private OtpMailSender mail;
    private PasswordService service;
    private User user;
    /** OTP rõ mà service gửi qua mail (bắt từ mock). */
    private final AtomicReference<String> sentOtp = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        otps = mock(PasswordOtpRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        mail = mock(OtpMailSender.class);
        service = new PasswordService(users, otps, refreshTokens, ENCODER, mail, CLOCK);

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
            sentOtp.set(inv.getArgument(2));
            return null;
        }).when(mail).sendOtp(anyString(), anyString(), anyString(), anyLong());
    }

    /** Gửi OTP rồi để repository trả về đúng bản ghi vừa lưu. */
    private PasswordOtp requestOtp() {
        service.forgotPassword("thukho@tigernet.vn");
        ArgumentCaptor<PasswordOtp> saved = ArgumentCaptor.forClass(PasswordOtp.class);
        verify(otps).save(saved.capture());
        when(otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(7)).thenReturn(Optional.of(saved.getValue()));
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
    }

    @Test
    void forgotPasswordForUnknownOrDisabledEmailIsSilent() {
        service.forgotPassword("khong-ton-tai@x.vn");
        user.setActive(false);
        service.forgotPassword("thukho@tigernet.vn");

        verify(otps, never()).save(any());
        verify(mail, never()).sendOtp(anyString(), anyString(), anyString(), anyLong());
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
    void mailFailureReturnsServiceUnavailable() {
        doThrow(new MailSendException("down")).when(mail).sendOtp(anyString(), anyString(), anyString(), anyLong());
        assertThatThrownBy(() -> service.forgotPassword("thukho@tigernet.vn"))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void correctOtpIssuesOneTimeResetTokenThenResetsPassword() {
        PasswordOtp otp = requestOtp();

        ResetTokenResponse res = service.verifyOtp("thukho@tigernet.vn", sentOtp.get());
        assertThat(res.resetToken()).isNotBlank();
        assertThat(otp.getUsedAt()).isEqualTo(NOW);
        assertThat(otp.getResetTokenHash()).isEqualTo(TokenHasher.sha256(res.resetToken()));

        when(otps.findByResetTokenHash(otp.getResetTokenHash())).thenReturn(Optional.of(otp));
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
        when(otps.findByResetTokenHash(eq(TokenHasher.sha256("tok")))).thenReturn(Optional.of(otp));

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
