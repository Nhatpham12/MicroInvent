package com.microinvent.identity.web;

import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.web.ApiResponse;
import com.microinvent.identity.service.SettingService;
import com.microinvent.identity.web.dto.SettingResponse;
import com.microinvent.identity.web.dto.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET: ADMIN, MANAGER; PUT: ADMIN (SecurityConfig). */
@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1/settings")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @Operation(summary = "Đọc cấu hình hệ thống (số ngày giữ hàng, ngưỡng duyệt chuyển kho...)")
    @GetMapping
    public ApiResponse<List<SettingResponse>> list() {
        return ApiResponse.ok(settingService.list());
    }

    @Operation(summary = "Cập nhật cấu hình (chỉ key đã có, giá trị số nguyên không âm)")
    @PutMapping
    public ApiResponse<List<SettingResponse>> update(@Valid @RequestBody UpdateSettingsRequest req) {
        return ApiResponse.ok(settingService.update(req.settings(),
                UserContextHolder.get().userId().intValue()));
    }
}
