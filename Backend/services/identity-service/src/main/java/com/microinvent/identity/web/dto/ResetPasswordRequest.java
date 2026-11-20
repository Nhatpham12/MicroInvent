package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu reset token") @Size(max = 100) String resetToken,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
