package com.microinvent.identity.repository;

import com.microinvent.identity.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
