package com.microinvent.identity.service;

import com.microinvent.identity.domain.Permission;
import com.microinvent.identity.repository.RoleRepository;
import com.microinvent.identity.web.dto.RoleResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh sách vai trò & quyền (chỉ đọc; quyền theo vai trò khai báo bằng migration). */
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roles;

    public RoleService(RoleRepository roles) {
        this.roles = roles;
    }

    public List<RoleResponse> list() {
        return roles.findAllWithPermissions().stream()
                .map(r -> new RoleResponse(r.getId(), r.getCode(), r.getName(), r.isAllWarehouses(),
                        r.getPermissions().stream()
                                .sorted(Comparator.comparing(Permission::getCode))
                                .map(p -> new RoleResponse.PermissionResponse(p.getCode(), p.getDescription()))
                                .toList()))
                .toList();
    }
}
