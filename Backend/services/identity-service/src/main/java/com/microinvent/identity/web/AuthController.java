package com.microinvent.identity.web;

import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.identity.service.AuthService;
import com.microinvent.identity.service.AuthService.ClientInfo;
import com.microinvent.identity.service.UserScopeService;
import com.microinvent.identity.web.dto.LoginRequest;
import com.microinvent.identity.web.dto.MeResponse;
import com.microinvent.identity.web.dto.RefreshRequest;
import com.microinvent.identity.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserScopeService scopeService;

    public AuthController(AuthService authService, UserScopeService scopeService) {
        this.authService = authService;
        this.scopeService = scopeService;
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
        return ApiResponse.ok(scopeService.me(UserContextHolder.get().userId().intValue()));
    }

    static ClientInfo client(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        return new ClientInfo(ip, http.getHeader(HttpHeaders.USER_AGENT));
    }
}
