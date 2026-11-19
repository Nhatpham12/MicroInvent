package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sửa thông tin + vai trò. Tên đăng nhập không đổi; mật khẩu và kho có API riêng. */
public record UpdateUserRequest(
        @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ") @Size(max = 100)
        String email,
        @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100) String fullName,
        @Size(max = 20) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "Số điện thoại không hợp lệ") String phone,
        @NotBlank(message = "Vui lòng chọn vai trò") @Size(max = 30) String role) {
}
