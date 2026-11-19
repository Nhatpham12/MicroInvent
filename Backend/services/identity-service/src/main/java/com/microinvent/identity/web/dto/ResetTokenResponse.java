package com.microinvent.identity.web.dto;

/** Token dùng 1 lần cho /auth/reset-password; expiresIn tính bằng giây. */
public record ResetTokenResponse(String resetToken, long expiresIn) {
}
