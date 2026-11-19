package com.microinvent.identity.web.dto;

import java.time.LocalDateTime;

/** Một dòng trong danh sách người dùng. locked = đang bị khóa tạm do đăng nhập sai. */
public record UserSummaryResponse(Integer id, String username, String email, String fullName, String phone,
        String role, String roleName, boolean active, boolean locked, LocalDateTime lockedUntil,
        Integer defaultWarehouseId, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
}
