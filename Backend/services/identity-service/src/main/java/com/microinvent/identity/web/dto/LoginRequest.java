package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param username tên đăng nhập hoặc email */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập hoặc email") @Size(max = 100) String username,
        @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(max = 100) String password) {
}
