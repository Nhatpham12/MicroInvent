package com.microinvent.identity.service;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.SystemSetting;
import com.microinvent.identity.repository.SystemSettingRepository;
import com.microinvent.identity.web.dto.SettingResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấu hình hệ thống (bảng system_settings): số ngày giữ hàng, ngưỡng duyệt chuyển kho, số lần đăng nhập sai...
 * Chỉ sửa được key đã có (key mới thêm bằng migration). Mọi cấu hình hiện tại là số nguyên không âm.
 */
@Service
@Transactional
public class SettingService {

    public static final String MAX_FAILED_LOGIN = "MAX_FAILED_LOGIN";
    public static final String LOCK_MINUTES = "LOCK_MINUTES";

    /** Các key phải ≥ 1 (bằng 0 sẽ khóa tài khoản ngay hoặc không khóa được). */
    private static final Set<String> POSITIVE_KEYS = Set.of(MAX_FAILED_LOGIN, LOCK_MINUTES);

    private final SystemSettingRepository repository;
    private final Clock clock;

    public SettingService(SystemSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repository.findById(key).map(s -> {
            try {
                return Integer.parseInt(s.getValue().trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SystemSetting::getKey))
                .map(SettingService::toResponse)
                .toList();
    }

    /** Kiểm tra hết rồi mới ghi: một giá trị sai thì không key nào bị đổi. */
    public List<SettingResponse> update(Map<String, String> values, Integer updatedBy) {
        Map<String, SystemSetting> existing = repository.findAllById(values.keySet()).stream()
                .collect(Collectors.toMap(SystemSetting::getKey, Function.identity()));
        Map<String, String> errors = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String error = validate(key, value, existing.containsKey(key));
            if (error != null) {
                errors.put(key, error);
            }
        });
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    errors);
        }
        values.forEach((key, value) -> {
            SystemSetting s = existing.get(key);
            s.setValue(value.trim());
            s.setUpdatedBy(updatedBy);
            s.setUpdatedAt(LocalDateTime.now(clock));
        });
        return list();
    }

    private static String validate(String key, String value, boolean exists) {
        if (!exists) {
            return "Cấu hình không tồn tại";
        }
        if (value == null || !value.trim().matches("\\d{1,9}")) {
            return "Giá trị phải là số nguyên không âm";
        }
        if (POSITIVE_KEYS.contains(key) && Integer.parseInt(value.trim()) < 1) {
            return "Giá trị phải lớn hơn 0";
        }
        return null;
    }

    private static SettingResponse toResponse(SystemSetting s) {
        return new SettingResponse(s.getKey(), s.getValue(), s.getDescription(), s.getUpdatedBy(),
                s.getUpdatedAt());
    }
}
