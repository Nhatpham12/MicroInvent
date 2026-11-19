package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param email email (hoặc tên đăng nhập) của tài khoản cần đặt lại mật khẩu */
public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập email") @Size(max = 100) String email) {
}
