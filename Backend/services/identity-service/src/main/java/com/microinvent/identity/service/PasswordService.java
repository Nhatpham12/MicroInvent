package com.microinvent.identity.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.PasswordOtp;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.repository.PasswordOtpRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.ResetTokenResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quên mật khẩu bằng OTP qua email, đổi mật khẩu, admin đặt lại mật khẩu (SRS 3.1.1).
 * <ol>
 * <li>forgot-password: sinh OTP 6 số (hiệu lực 5 phút, lưu bcrypt), gửi email. Luôn trả OK dù email có tồn
 * tại hay không để không lộ tài khoản. Gửi lại phải cách lần trước ≥ 60 giây.</li>
 * <li>verify-otp: đúng thì cấp reset token (dùng 1 lần, 15 phút); sai 5 lần thì OTP bị hủy.</li>
 * <li>reset-password: đặt mật khẩu mới bằng reset token.</li>
 * </ol>
 * Mỗi lần đổi mật khẩu: mở khóa đăng nhập và thu hồi mọi refresh token (đăng xuất mọi thiết bị).
 * noRollbackFor để vẫn lưu số lần nhập sai OTP khi ném BusinessException.
 */
@Service
@Transactional(noRollbackFor = BusinessException.class)
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration OTP_RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
    static final int OTP_MAX_ATTEMPTS = 5;

    private final UserRepository users;
    private final PasswordOtpRepository otps;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final OtpMailSender mailSender;
    private final Clock clock;

    public PasswordService(UserRepository users, PasswordOtpRepository otps, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, OtpMailSender mailSender, Clock clock) {
        this.users = users;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.clock = clock;
    }

    public void forgotPassword(String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<User> found = users.findByLogin(email.trim()).filter(User::isActive);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        boolean tooSoon = otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .map(last -> last.getCreatedAt().isAfter(now.minus(OTP_RESEND_INTERVAL)))
                .orElse(false);
        if (tooSoon) {
            return;
        }
        otps.invalidateAll(user.getId(), now);

        String otp = "%06d".formatted(RANDOM.nextInt(1_000_000));
        PasswordOtp o = new PasswordOtp();
        o.setUserId(user.getId());
        o.setOtpHash(passwordEncoder.encode(otp));
        o.setExpiresAt(now.plus(OTP_TTL));
        o.setCreatedAt(now);
        otps.save(o);
        try {
            mailSender.sendOtp(user.getEmail(), user.getFullName(), otp, OTP_TTL.toMinutes());
        } catch (MailException ex) {
            log.error("Không gửi được email OTP cho user {}", user.getId(), ex);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Không gửi được email, vui lòng thử lại sau");
        }
    }

    public ResetTokenResponse verifyOtp(String email, String otp) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<User> user = users.findByLogin(email.trim()).filter(User::isActive);
        Optional<PasswordOtp> current = user
                .flatMap(u -> otps.findFirstByUserIdOrderByCreatedAtDescIdDesc(u.getId()))
                .filter(o -> o.isUsable(now));
        if (current.isEmpty()) {
            // Vẫn chạy bcrypt để thời gian phản hồi không lộ email có tồn tại hay không.
            passwordEncoder.matches(otp, AuthService.DUMMY_HASH);
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }
        PasswordOtp o = current.get();
        if (!passwordEncoder.matches(otp, o.getOtpHash())) {
            o.setAttempts(o.getAttempts() + 1);
            int remaining = OTP_MAX_ATTEMPTS - o.getAttempts();
            if (remaining <= 0) {
                o.setUsedAt(now);
            }
            throw new BusinessException(ErrorCode.OTP_INVALID, ErrorCode.OTP_INVALID.defaultMessage(),
                    Map.of("remainingAttempts", Math.max(remaining, 0)));
        }
        String rawToken = TokenHasher.newToken();
        o.setUsedAt(now);
        o.setResetTokenHash(TokenHasher.sha256(rawToken));
        o.setResetExpiresAt(now.plus(RESET_TOKEN_TTL));
        return new ResetTokenResponse(rawToken, RESET_TOKEN_TTL.toSeconds());
    }

    public void resetPassword(String resetToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordOtp o = otps.findByResetTokenHash(TokenHasher.sha256(resetToken))
                .filter(x -> x.getResetExpiresAt() != null && x.getResetExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        o.setResetTokenHash(null);
        o.setResetExpiresAt(null);
        User user = users.findById(o.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        setPassword(user, newPassword, now);
    }

    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        setPassword(user, newPassword, LocalDateTime.now(clock));
    }

    /** Admin đặt lại mật khẩu cho người dùng khác (không cần mật khẩu cũ). */
    public void adminResetPassword(User user, String newPassword) {
        setPassword(user, newPassword, LocalDateTime.now(clock));
    }

    private void setPassword(User user, String rawPassword, LocalDateTime now) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        refreshTokens.revokeAllByUserId(user.getId(), now);
    }
}
