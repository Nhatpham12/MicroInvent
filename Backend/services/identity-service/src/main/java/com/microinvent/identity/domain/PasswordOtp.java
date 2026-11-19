package com.microinvent.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Mã OTP quên mật khẩu. Chỉ lưu bcrypt của OTP; sau khi xác thực đúng thì cấp reset token (lưu SHA-256)
 * để gọi /auth/reset-password một lần.
 */
@Entity
@Table(name = "password_otps")
@Getter
@Setter
public class PasswordOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Đã xác thực xong hoặc bị vô hiệu (gửi OTP mới, sai quá số lần). */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(name = "reset_expires_at")
    private LocalDateTime resetExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
