package com.microinvent.common.security;

import java.util.Set;

/** Nội dung access token: ai, thuộc tenant nào, vai trò gì, được vào những kho nào. */
public record JwtPrincipal(Long userId, String username, String tenantId, Set<String> roles, Set<Long> warehouseIds,
        boolean allWarehouses) {

    public JwtPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        warehouseIds = warehouseIds == null ? Set.of() : Set.copyOf(warehouseIds);
    }
}
