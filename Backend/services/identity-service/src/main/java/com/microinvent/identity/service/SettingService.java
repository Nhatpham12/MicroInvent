package com.microinvent.identity.service;

import com.microinvent.identity.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

/** Đọc cấu hình hệ thống (bảng system_settings). CRUD /api/v1/settings làm ở W3. */
@Service
public class SettingService {

    public static final String MAX_FAILED_LOGIN = "MAX_FAILED_LOGIN";
    public static final String LOCK_MINUTES = "LOCK_MINUTES";

    private final SystemSettingRepository repository;

    public SettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public int getInt(String key, int defaultValue) {
        return repository.findById(key).map(s -> {
            try {
                return Integer.parseInt(s.getValue().trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }
}
