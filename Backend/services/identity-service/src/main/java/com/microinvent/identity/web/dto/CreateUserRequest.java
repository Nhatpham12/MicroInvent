package com.microinvent.identity.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tạo người dùng. warehouses / defaultWarehouseId có thể bỏ trống và gán sau qua PUT /users/{id}/warehouses. */
public record CreateUserRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập") @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._\\-]+$", message = "Tên đăng nhập chỉ gồm chữ, số, dấu chấm, _ và -")
        String username,
        @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ") @Size(max = 100)
        String email,
        @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100) String fullName,
        @Size(max = 20) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "Số điện thoại không hợp lệ") String phone,
        @NotBlank(message = "Vui lòng chọn vai trò") @Size(max = 30) String role,
        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,
        @Size(max = 100) List<@Valid WarehouseAccessRequest> warehouses,
        @Positive Integer defaultWarehouseId) {
}
