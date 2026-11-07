package com.microinvent.identity.web;

import com.microinvent.common.web.ApiResponse;
import com.microinvent.identity.service.UserScopeService;
import com.microinvent.identity.web.dto.UserScopeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ cho service khác gọi (header X-Internal-Key, kiểm bởi InternalApiKeyFilter của common-lib). */
@Tag(name = "Internal")
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserScopeService scopeService;

    public InternalUserController(UserScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @Operation(summary = "Phạm vi kho + quyền của user")
    @GetMapping("/{id}/scope")
    public ApiResponse<UserScopeResponse> scope(@PathVariable Integer id) {
        return ApiResponse.ok(scopeService.scope(id));
    }
}
