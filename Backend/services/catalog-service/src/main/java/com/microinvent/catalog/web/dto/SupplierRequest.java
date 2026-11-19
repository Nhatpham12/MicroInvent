package com.microinvent.catalog.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tạo / sửa nhà cung cấp. Khi sửa, code bị bỏ qua (mã không đổi sau khi tạo).
 *
 * @param code bỏ trống thì tự sinh (NCC00001...)
 * @param bankAccountNo khi sửa: null = giữ nguyên số cũ, chuỗi rỗng = xóa, còn lại = thay số mới
 */
public record SupplierRequest(
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_.\\-]*$", message = "Mã chỉ gồm chữ, số, dấu chấm, _ và -")
        String code,
        @NotBlank(message = "Vui lòng nhập tên nhà cung cấp") @Size(max = 255) String name,
        @NotBlank(message = "Vui lòng nhập mã số thuế")
        @Pattern(regexp = "^\\d{10}(-\\d{3})?$", message = "Mã số thuế gồm 10 số hoặc 10 số-3 số") String taxCode,
        @Size(max = 255) String address,
        @Size(max = 50) @Pattern(regexp = "^[0-9+()\\-./ ]*$", message = "Số điện thoại/fax không hợp lệ")
        String phoneFax,
        @Size(max = 100) @Email(message = "Email không hợp lệ") String email,
        @Pattern(regexp = "^[0-9 .\\-]{0,30}$", message = "Số tài khoản chỉ gồm chữ số") String bankAccountNo,
        @Size(max = 100) String bankAccountName,
        @Size(max = 100) String bankName,
        @Size(max = 5000) String note) {
}
