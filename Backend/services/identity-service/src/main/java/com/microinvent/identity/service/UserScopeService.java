package com.microinvent.identity.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.Permission;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.domain.UserWarehouse;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.MeResponse;
import com.microinvent.identity.web.dto.UserScopeResponse;
import com.microinvent.identity.web.dto.WarehouseAccess;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dựng thông tin người dùng + phạm vi (vai trò, quyền, kho) cho /auth/me và /internal/users/{id}/scope. */
@Service
@Transactional(readOnly = true)
public class UserScopeService {

    private final UserRepository users;

    public UserScopeService(UserRepository users) {
        this.users = users;
    }

    public User loadWithScope(Integer userId) {
        return users.findWithScopeById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                "Không tìm thấy người dùng"));
    }

    public MeResponse me(Integer userId) {
        return toMe(loadWithScope(userId));
    }

    public UserScopeResponse scope(Integer userId) {
        User u = loadWithScope(userId);
        return new UserScopeResponse(u.getId(), u.getUsername(), u.isActive(), u.getRole().getCode(),
                permissions(u), u.getRole().isAllWarehouses(), warehouses(u), u.getDefaultWarehouseId());
    }

    static MeResponse toMe(User u) {
        return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole().getCode(), u.getRole().getName(), permissions(u), u.getRole().isAllWarehouses(),
                warehouses(u), u.getDefaultWarehouseId(), u.getLastLoginAt());
    }

    static List<String> permissions(User u) {
        return u.getRole().getPermissions().stream().map(Permission::getCode).sorted().toList();
    }

    static List<WarehouseAccess> warehouses(User u) {
        return u.getWarehouses().stream()
                .sorted(Comparator.comparing(UserWarehouse::getWarehouseId))
                .map(w -> new WarehouseAccess(w.getWarehouseId(), w.isCanWrite()))
                .toList();
    }
}
