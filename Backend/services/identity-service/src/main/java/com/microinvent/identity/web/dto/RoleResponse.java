package com.microinvent.identity.web.dto;

import java.util.List;

public record RoleResponse(Integer id, String code, String name, boolean allWarehouses,
        List<PermissionResponse> permissions) {

    public record PermissionResponse(String code, String description) {
    }
}
