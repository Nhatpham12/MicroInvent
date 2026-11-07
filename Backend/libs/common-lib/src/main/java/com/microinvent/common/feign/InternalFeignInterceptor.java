package com.microinvent.common.feign;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.web.HeaderNames;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.stream.Collectors;
import org.slf4j.MDC;

/**
 * Gắn header cho mọi lời gọi Feign giữa các service: X-Internal-Key, X-Tenant-Id, X-Trace-Id và
 * thông tin người dùng hiện tại (để service đích kiểm quyền/ghi log đúng người).
 */
public class InternalFeignInterceptor implements RequestInterceptor {

    private final String internalKey;

    public InternalFeignInterceptor(String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public void apply(RequestTemplate t) {
        t.header(HeaderNames.INTERNAL_KEY, internalKey);
        String tenant = TenantContext.get();
        if (tenant != null) {
            t.header(HeaderNames.TENANT_ID, tenant);
        }
        String traceId = MDC.get(HeaderNames.MDC_TRACE_ID);
        if (traceId != null) {
            t.header(HeaderNames.TRACE_ID, traceId);
        }
        UserContext user = UserContextHolder.get();
        if (user.userId() != null) {
            t.header(HeaderNames.USER_ID, String.valueOf(user.userId()));
            if (user.username() != null) {
                t.header(HeaderNames.USER_NAME, user.username());
            }
            t.header(HeaderNames.USER_ROLES, String.join(",", user.roles()));
            t.header(HeaderNames.WAREHOUSE_IDS,
                    user.warehouseIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            t.header(HeaderNames.ALL_WAREHOUSES, String.valueOf(user.allWarehouses()));
            if (user.currentWarehouseId() != null) {
                t.header(HeaderNames.WAREHOUSE_ID, String.valueOf(user.currentWarehouseId()));
            }
        }
    }
}
