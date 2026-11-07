package com.microinvent.identity.web.dto;

/** Kết quả login/refresh. accessToken gửi ở header Authorization: Bearer; refreshToken dùng cho /auth/refresh. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
        MeResponse user) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn, MeResponse user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
