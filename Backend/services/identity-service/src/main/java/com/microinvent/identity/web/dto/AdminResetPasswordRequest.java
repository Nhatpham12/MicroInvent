package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminResetPasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
