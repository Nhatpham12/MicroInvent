package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "Thiếu refresh token") String refreshToken) {
}
