package com.microinvent.identity.web.dto;

import java.util.List;

/** Phạm vi quyền của user cho service khác gọi qua /internal/users/{id}/scope. */
public record UserScopeResponse(Integer userId, String username, boolean active, String role,
        List<String> permissions, boolean allWarehouses, List<WarehouseAccess> warehouses,
        Integer defaultWarehouseId) {
}
