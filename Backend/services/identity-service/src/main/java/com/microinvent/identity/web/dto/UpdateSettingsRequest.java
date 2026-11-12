package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Ví dụ: { "settings": { "HOLD_DAYS": "5", "DEBT_OVERDUE_DAYS": "45" } }. Chỉ sửa được key đã có. */
public record UpdateSettingsRequest(
        @NotEmpty(message = "Thiếu danh sách cấu hình") @Size(max = 50) Map<String, String> settings) {
}
