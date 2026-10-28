package com.microinvent.tenant.starter;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** Chọn DataSource theo TenantContext; không có tenant trong request thì dùng tenant mặc định. */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final String defaultTenant;
    private final Set<String> tenants;

    public TenantRoutingDataSource(String defaultTenant, Set<String> tenants) {
        this.defaultTenant = defaultTenant;
        this.tenants = tenants;
        setLenientFallback(false);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String tenant = TenantContext.get();
        if (tenant == null) {
            return defaultTenant;
        }
        if (!tenants.contains(tenant)) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND, "Tenant không tồn tại: " + tenant);
        }
        return tenant;
    }
}
