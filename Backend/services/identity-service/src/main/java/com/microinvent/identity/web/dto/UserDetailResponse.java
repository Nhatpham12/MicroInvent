package com.microinvent.identity.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết người dùng + kho được gán (allWarehouses = vai trò xem mọi kho). */
public record UserDetailResponse(Integer id, String username, String email, String fullName, String phone,
        String role, String roleName, boolean allWarehouses, boolean active, boolean locked,
        LocalDateTime lockedUntil, List<WarehouseAccess> warehouses, Integer defaultWarehouseId,
        LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
