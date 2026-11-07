package com.microinvent.identity.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thông tin người dùng hiện tại. allWarehouses = true (ADMIN/MANAGER/ACCOUNTANT) thì warehouses có thể rỗng
 * và FE lấy danh sách kho từ inventory-service.
 */
public record MeResponse(Integer id, String username, String email, String fullName, String phone, String role,
        String roleName, List<String> permissions, boolean allWarehouses, List<WarehouseAccess> warehouses,
        Integer defaultWarehouseId, LocalDateTime lastLoginAt) {
}
