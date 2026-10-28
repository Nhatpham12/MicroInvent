package com.microinvent.common.context;

import java.util.Set;

/**
 * Thông tin người dùng do api-gateway xác thực JWT rồi chuyển xuống qua header.
 *
 * @param currentWarehouseId kho đang làm việc (header X-Warehouse-Id), có thể null
 */
public record UserContext(Long userId, String username, Set<String> roles, Set<Long> warehouseIds,
        boolean allWarehouses, Long currentWarehouseId) {

    public static final UserContext ANONYMOUS = new UserContext(null, null, Set.of(), Set.of(), false, null);

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean canAccessWarehouse(Long warehouseId) {
        return allWarehouses || (warehouseId != null && warehouseIds.contains(warehouseId));
    }
}
