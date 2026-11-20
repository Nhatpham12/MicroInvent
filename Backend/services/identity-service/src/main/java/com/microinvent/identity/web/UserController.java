package com.microinvent.identity.web;

import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.common.web.PageRequests;
import com.microinvent.common.web.PageResponse;
import com.microinvent.identity.service.UserService;
import com.microinvent.identity.web.dto.AccessStatsResponse;
import com.microinvent.identity.web.dto.AdminResetPasswordRequest;
import com.microinvent.identity.web.dto.AssignWarehousesRequest;
import com.microinvent.identity.web.dto.CreateUserRequest;
import com.microinvent.identity.web.dto.UpdateUserRequest;
import com.microinvent.identity.web.dto.UserDetailResponse;
import com.microinvent.identity.web.dto.UserStatusRequest;
import com.microinvent.identity.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý người dùng – chỉ ADMIN (SecurityConfig chặn /api/v1/users/** cho vai trò khác). */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("id", "username", "fullName", "email", "createdAt",
            "lastLoginAt");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Danh sách người dùng (lọc vai trò, kho, trạng thái, tìm kiếm, phân trang)")
    @GetMapping
    public ApiResponse<PageResponse<UserSummaryResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role, @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer warehouseId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        return ApiResponse.ok(userService.list(keyword, role, active, warehouseId,
                PageRequests.of(page, size, sort, SORTABLE, Sort.by("username"))));
    }

    @Operation(summary = "Lượng truy cập theo tháng (từ login_histories), mặc định năm hiện tại")
    @GetMapping("/stats/access")
    public ApiResponse<AccessStatsResponse> accessStats(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(userService.accessStats(year != null ? year : LocalDate.now().getYear()));
    }

    @Operation(summary = "Tạo người dùng")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDetailResponse> create(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.ok(userService.create(req));
    }

    @Operation(summary = "Chi tiết người dùng + kho được gán")
    @GetMapping("/{id}")
    public ApiResponse<UserDetailResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(userService.get(id));
    }

    @Operation(summary = "Cập nhật thông tin, vai trò (không đổi tên đăng nhập)")
    @PutMapping("/{id}")
    public ApiResponse<UserDetailResponse> update(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.ok(userService.update(id, req, UserContextHolder.get().userId()));
    }

    @Operation(summary = "Vô hiệu hóa / kích hoạt, mở khóa đăng nhập")
    @PatchMapping("/{id}/status")
    public ApiResponse<UserDetailResponse> changeStatus(@PathVariable Integer id,
            @Valid @RequestBody UserStatusRequest req) {
        return ApiResponse.ok(userService.changeStatus(id, req, UserContextHolder.get().userId()));
    }

    @Operation(summary = "Gán danh sách kho + quyền ghi + kho mặc định (thay toàn bộ)")
    @PutMapping("/{id}/warehouses")
    public ApiResponse<UserDetailResponse> assignWarehouses(@PathVariable Integer id,
            @Valid @RequestBody AssignWarehousesRequest req) {
        return ApiResponse.ok(userService.assignWarehouses(id, req));
    }

    @Operation(summary = "Admin đặt lại mật khẩu (đăng xuất mọi thiết bị của người dùng)")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Integer id,
            @Valid @RequestBody AdminResetPasswordRequest req) {
        userService.resetPassword(id, req.newPassword());
        return ApiResponse.ok(null);
    }
}
