package com.microinvent.common.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health – health check nhẹ cho FE/monitor (chi tiết hơn ở /actuator/health). */
@RestController
public class HealthController {

    private final String serviceName;

    public HealthController(String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", serviceName, "time", Instant.now().toString()));
    }
}
