package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.SystemSetting;
import com.microinvent.identity.repository.SystemSettingRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingServiceTest {

    private SettingService service;
    private SystemSetting holdDays;
    private SystemSetting maxFailed;

    @BeforeEach
    void setUp() {
        SystemSettingRepository repo = mock(SystemSettingRepository.class);
        service = new SettingService(repo, Clock.systemDefaultZone());
        holdDays = setting("HOLD_DAYS", "3");
        maxFailed = setting("MAX_FAILED_LOGIN", "5");
        when(repo.findAllById(any())).thenReturn(List.of(holdDays, maxFailed));
        when(repo.findAll()).thenReturn(List.of(holdDays, maxFailed));
    }

    private static SystemSetting setting(String key, String value) {
        SystemSetting s = new SystemSetting();
        s.setKey(key);
        s.setValue(value);
        return s;
    }

    @Test
    void updatesValuesAndRecordsWhoChanged() {
        service.update(Map.of("HOLD_DAYS", " 5 "), 1);
        assertThat(holdDays.getValue()).isEqualTo("5");
        assertThat(holdDays.getUpdatedBy()).isEqualTo(1);
        assertThat(holdDays.getUpdatedAt()).isNotNull();
    }

    @Test
    void oneInvalidValueRejectsWholeRequest() {
        assertThatThrownBy(() -> service.update(Map.of("HOLD_DAYS", "7", "MAX_FAILED_LOGIN", "0"), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(holdDays.getValue()).isEqualTo("3");

        assertThatThrownBy(() -> service.update(Map.of("HOLD_DAYS", "-1"), 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(Map.of("UNKNOWN_KEY", "1"), 1))
                .isInstanceOf(BusinessException.class);
    }
}
