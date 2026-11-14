package com.microinvent.identity.web.dto;

import java.time.LocalDateTime;

public record SettingResponse(String key, String value, String description, Integer updatedBy,
        LocalDateTime updatedAt) {
}
