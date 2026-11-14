package com.microinvent.identity.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.identity.domain.Role;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.domain.UserWarehouse;
import com.microinvent.identity.repository.LoginHistoryRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.RoleRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.AccessStatsResponse;
import com.microinvent.identity.web.dto.AssignWarehousesRequest;
import com.microinvent.identity.web.dto.CreateUserRequest;
import com.microinvent.identity.web.dto.MeResponse;
import com.microinvent.identity.web.dto.UpdateUserRequest;
import com.microinvent.identity.web.dto.UserDetailResponse;
import com.microinvent.identity.web.dto.UserStatusRequest;
import com.microinvent.identity.web.dto.UserSummaryResponse;
import com.microinvent.identity.web.dto.WarehouseAccessRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Quản lý người dùng (SRS 3.1.2, 3.10.3): CRUD, vô hiệu hóa / mở khóa, gán kho + quyền ghi + kho mặc định,
 * đổi kho mặc định của chính mình, thống kê truy cập theo tháng.
 * <p>
 * Quy tắc kho: vai trò không xem mọi kho (STOREKEEPER, SALES) chỉ được chọn kho mặc định trong số kho được gán.
 * Admin không thể tự vô hiệu hóa hoặc tự bỏ vai trò ADMIN của mình (tránh mất quyền quản trị).
 */
@Service
@Transactional
public class UserService {

    private static final String ADMIN = "ADMIN";

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final LoginHistoryRepository loginHistories;
    private final UserScopeService scopeService;
    private final PasswordService passwordService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            LoginHistoryRepository loginHistories, UserScopeService scopeService, PasswordService passwordService,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.loginHistories = loginHistories;
        this.scopeService = scopeService;
        this.passwordService = passwordService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> list(String keyword, String role, Boolean active, Integer warehouseId,
            Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        String roleCode = StringUtils.hasText(role) ? role.trim().toUpperCase(Locale.ROOT) : null;
        return PageResponse.of(users.search(likePattern(keyword), roleCode, active, warehouseId, pageable)
                .map(u -> toSummary(u, now)));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse get(Integer id) {
        return toDetail(scopeService.loadWithScope(id));
    }

    public UserDetailResponse create(CreateUserRequest req) {
        String username = req.username().trim();
        String email = req.email().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email đã được dùng cho tài khoản khác");
        }
        Role role = findRole(req.role());
        List<WarehouseAccessRequest> warehouses = req.warehouses() == null ? List.of() : req.warehouses();
        checkWarehouses(role, warehouses, req.defaultWarehouseId());

        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDefaultWarehouseId(req.defaultWarehouseId());
        applyWarehouses(u, warehouses);
        return toDetail(users.save(u));
    }

    public UserDetailResponse update(Integer id, UpdateUserRequest req, Long currentUserId) {
        User u = scopeService.loadWithScope(id);
        String email = req.email().trim();
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email đã được dùng cho tài khoản khác");
        }
        Role role = findRole(req.role());
        if (isSelf(u, currentUserId) && ADMIN.equals(u.getRole().getCode()) && !ADMIN.equals(role.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể tự bỏ vai trò quản trị của chính mình");
        }
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(role);
        // Đổi sang vai trò chỉ thao tác kho được gán: bỏ kho mặc định nếu kho đó không còn trong danh sách.
        if (!role.isAllWarehouses() && u.getDefaultWarehouseId() != null
                && u.getWarehouses().stream().noneMatch(w -> w.getWarehouseId().equals(u.getDefaultWarehouseId()))) {
            u.setDefaultWarehouseId(null);
        }
        return toDetail(u);
    }

    public UserDetailResponse changeStatus(Integer id, UserStatusRequest req, Long currentUserId) {
        User u = scopeService.loadWithScope(id);
        LocalDateTime now = LocalDateTime.now(clock);
        if (Boolean.FALSE.equals(req.active())) {
            if (isSelf(u, currentUserId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể tự vô hiệu hóa tài khoản của mình");
            }
            u.setActive(false);
            refreshTokens.revokeAllByUserId(u.getId(), now);
        } else if (Boolean.TRUE.equals(req.active())) {
            u.setActive(true);
        }
        if (Boolean.TRUE.equals(req.unlock())) {
            u.setFailedLoginCount(0);
            u.setLockedUntil(null);
        }
        return toDetail(u);
    }

    public UserDetailResponse assignWarehouses(Integer id, AssignWarehousesRequest req) {
        User u = scopeService.loadWithScope(id);
        checkWarehouses(u.getRole(), req.warehouses(), req.defaultWarehouseId());
        applyWarehouses(u, req.warehouses());
        u.setDefaultWarehouseId(req.defaultWarehouseId());
        return toDetail(u);
    }

    public void resetPassword(Integer id, String newPassword) {
        User u = users.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
        passwordService.adminResetPassword(u, newPassword);
    }

    /** Người dùng tự đổi kho mặc định / kho đang làm việc. */
    public MeResponse changeDefaultWarehouse(Integer userId, Integer warehouseId) {
        User u = scopeService.loadWithScope(userId);
        boolean assigned = u.getWarehouses().stream().anyMatch(w -> w.getWarehouseId().equals(warehouseId));
        if (!u.getRole().isAllWarehouses() && !assigned) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
        u.setDefaultWarehouseId(warehouseId);
        return UserScopeService.toMe(u);
    }

    @Transactional(readOnly = true)
    public AccessStatsResponse accessStats(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Năm không hợp lệ");
        }
        LocalDateTime from = LocalDateTime.of(year, 1, 1, 0, 0);
        Map<Integer, LoginHistoryRepository.MonthlyAccess> byMonth = loginHistories
                .countByMonth(from, from.plusYears(1)).stream()
                .collect(Collectors.toMap(LoginHistoryRepository.MonthlyAccess::getMonth, Function.identity()));
        List<AccessStatsResponse.Month> months = IntStream.rangeClosed(1, 12).mapToObj(m -> {
            LoginHistoryRepository.MonthlyAccess a = byMonth.get(m);
            return a == null ? new AccessStatsResponse.Month(m, 0, 0, 0)
                    : new AccessStatsResponse.Month(m, nz(a.getSuccessCount()), nz(a.getFailedCount()),
                            nz(a.getActiveUsers()));
        }).toList();
        return new AccessStatsResponse(year, months);
    }

    /**
     * Đồng bộ collection kho theo danh sách mới: xóa kho không còn, sửa quyền ghi, thêm kho mới.
     * Không clear() rồi thêm lại vì Hibernate chạy INSERT trước DELETE → trùng khóa (user_id, warehouse_id).
     */
    private static void applyWarehouses(User u, List<WarehouseAccessRequest> requested) {
        Map<Integer, Boolean> wanted = new HashMap<>();
        requested.forEach(w -> wanted.put(w.warehouseId(), w.writable()));
        u.getWarehouses().removeIf(w -> !wanted.containsKey(w.getWarehouseId()));
        u.getWarehouses().forEach(w -> w.setCanWrite(wanted.remove(w.getWarehouseId())));
        wanted.forEach((warehouseId, canWrite) -> {
            UserWarehouse uw = new UserWarehouse();
            uw.setId(new UserWarehouse.Id(u.getId(), warehouseId));
            uw.setUser(u);
            uw.setCanWrite(canWrite);
            u.getWarehouses().add(uw);
        });
    }

    private static void checkWarehouses(Role role, List<WarehouseAccessRequest> warehouses, Integer defaultId) {
        long distinct = warehouses.stream().map(WarehouseAccessRequest::warehouseId).distinct().count();
        if (distinct != warehouses.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Danh sách kho bị trùng");
        }
        boolean inList = warehouses.stream().anyMatch(w -> w.warehouseId().equals(defaultId));
        if (defaultId != null && !role.isAllWarehouses() && !inList) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Kho mặc định phải nằm trong danh sách kho được gán");
        }
    }

    private Role findRole(String code) {
        return roles.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Vai trò không tồn tại"));
    }

    private static boolean isSelf(User u, Long currentUserId) {
        return currentUserId != null && Objects.equals(u.getId().longValue(), currentUserId);
    }

    /** "%abc%" chữ thường cho LIKE ... ESCAPE '!'; thoát ký tự đại diện để tìm đúng chuỗi đã gõ. */
    static String likePattern(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private UserSummaryResponse toSummary(User u, LocalDateTime now) {
        return new UserSummaryResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole().getCode(), u.getRole().getName(), u.isActive(), u.isLocked(now), u.getLockedUntil(),
                u.getDefaultWarehouseId(), u.getLastLoginAt(), u.getCreatedAt());
    }

    private UserDetailResponse toDetail(User u) {
        return new UserDetailResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole().getCode(), u.getRole().getName(), u.getRole().isAllWarehouses(), u.isActive(),
                u.isLocked(LocalDateTime.now(clock)), u.getLockedUntil(), UserScopeService.warehouses(u),
                u.getDefaultWarehouseId(), u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
