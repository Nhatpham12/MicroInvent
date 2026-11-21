package com.microinvent.identity.service;

/** Yêu cầu gửi email OTP – phát trong transaction của forgot-password, gửi thật sau khi commit. */
public record OtpMailRequested(Integer userId, String email, String fullName, String otp, long ttlMinutes) {

    @Override
    public String toString() {
        // Không in OTP ra log.
        return "OtpMailRequested[userId=" + userId + "]";
    }
}
