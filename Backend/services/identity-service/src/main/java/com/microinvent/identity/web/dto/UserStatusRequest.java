package com.microinvent.identity.web.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * @param active true = kích hoạt, false = vô hiệu hóa (null = giữ nguyên)
 * @param unlock true = mở khóa đăng nhập sai nhiều lần
 */
public record UserStatusRequest(Boolean active, Boolean unlock) {

    @AssertTrue(message = "Cần truyền active hoặc unlock")
    public boolean isAnyChange() {
        return active != null || Boolean.TRUE.equals(unlock);
    }
}
