package com.microinvent.identity.web;

import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.identity.service.AuthService;
import com.microinvent.identity.service.AuthService.ClientInfo;
import com.microinvent.identity.service.PasswordService;
import com.microinvent.identity.service.UserScopeService;
import com.microinvent.identity.service.UserService;
import com.microinvent.identity.web.dto.ChangePasswordRequest;
import com.microinvent.identity.web.dto.DefaultWarehouseRequest;
import com.microinvent.identity.web.dto.ForgotPasswordRequest;
import com.microinvent.identity.web.dto.LoginRequest;
import com.microinvent.identity.web.dto.MeResponse;
import com.microinvent.identity.web.dto.RefreshRequest;
import com.microinvent.identity.web.dto.ResetPasswordRequest;
import com.microinvent.identity.web.dto.ResetTokenResponse;
import com.microinvent.identity.web.dto.TokenResponse;
import com.microinvent.identity.web.dto.VerifyOtpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserScopeService scopeService;
    private final PasswordService passwordService;
    private final UserService userService;

    public AuthController(AuthService authService, UserScopeService scopeService, PasswordService passwordService,
            UserService userService) {
        this.authService = authService;
        this.scopeService = scopeService;
        this.passwordService = passwordService;
        this.userService = userService;
    }

    @Operation(summary = "Đăng nhập bằng username/email + mật khẩu (khóa 5 phút sau 5 lần sai)")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, client(http)));
    }

    @Operation(summary = "Cấp lại access token từ refresh token (refresh token cũ bị thu hồi)")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.refresh(req.refreshToken(), client(http)));
    }

    @Operation(summary = "Đăng xuất: thu hồi refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken(), UserContextHolder.get().userId());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Thông tin người dùng hiện tại, vai trò, quyền, kho được gán")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(scopeService.me(currentUserId()));
    }

    @Operation(summary = "Đổi kho mặc định / kho đang làm việc (phải là kho được gán, trừ vai trò xem mọi kho)")
    @PutMapping("/me/default-warehouse")
    public ApiResponse<MeResponse> changeDefaultWarehouse(@Valid @RequestBody DefaultWarehouseRequest req) {
        return ApiResponse.ok(userService.changeDefaultWarehouse(currentUserId(), req.warehouseId()));
    }

    @Operation(summary = "Quên mật khẩu: gửi OTP 6 số về email (luôn trả OK để không lộ tài khoản)")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordService.forgotPassword(req.email());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Xác thực OTP, trả reset token dùng 1 lần (sai 5 lần thì OTP bị hủy)")
    @PostMapping("/verify-otp")
    public ApiResponse<ResetTokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ApiResponse.ok(passwordService.verifyOtp(req.email(), req.otp()));
    }

    @Operation(summary = "Đặt lại mật khẩu bằng reset token (đăng xuất mọi thiết bị)")
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordService.resetPassword(req.resetToken(), req.newPassword());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Đổi mật khẩu (cần mật khẩu hiện tại; đăng xuất mọi thiết bị)")
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        passwordService.changePassword(currentUserId(), req.currentPassword(), req.newPassword());
        return ApiResponse.ok(null);
    }

    private static Integer currentUserId() {
        return UserContextHolder.get().userId().intValue();
    }

    static ClientInfo client(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        return new ClientInfo(ip, http.getHeader(HttpHeaders.USER_AGENT));
    }
}
