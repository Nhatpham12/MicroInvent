package com.microinvent.catalog.web.dto;

import com.microinvent.catalog.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tạo / sửa khách hàng. Khi sửa, code bị bỏ qua (mã không đổi sau khi tạo).
 *
 * @param code bỏ trống thì tự sinh (KH00001...)
 * @param customerType null = RETAIL; BUSINESS bắt buộc có mã số thuế
 * @param idCardNo CCCD 12 số (hoặc CMND 9 số). Khi sửa: null = giữ nguyên, chuỗi rỗng = xóa
 */
public record CustomerRequest(
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_.\\-]*$", message = "Mã chỉ gồm chữ, số, dấu chấm, _ và -")
        String code,
        @NotBlank(message = "Vui lòng nhập tên khách hàng") @Size(max = 255) String name,
        Customer.Type customerType,
        @Pattern(regexp = "^(\\d{10}(-\\d{3})?)?$", message = "Mã số thuế gồm 10 số hoặc 10 số-3 số")
        String taxCode,
        @Pattern(regexp = "^[0-9 .\\-]{0,20}$", message = "CCCD chỉ gồm chữ số") String idCardNo,
        @Size(max = 255) String address,
        @Pattern(regexp = "^((\\+84|0)\\d{9,10})?$", message = "Số điện thoại không hợp lệ") String phone,
        @Size(max = 100) @Email(message = "Email không hợp lệ") String email,
        @Size(max = 5000) String note) {
}
