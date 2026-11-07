package com.microinvent.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductGroupRequest(
        @NotBlank(message = "Vui lòng nhập mã nhóm") @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "Mã chỉ gồm chữ, số, _ và -") String code,
        @NotBlank(message = "Vui lòng nhập tên nhóm") @Size(max = 100) String name,
        @Size(max = 255) String description) {
}
