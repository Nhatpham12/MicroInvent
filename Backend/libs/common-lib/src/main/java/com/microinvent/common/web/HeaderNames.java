package com.microinvent.common.web;

/** Header nội bộ giữa api-gateway và các service. */
public final class HeaderNames {

    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String WAREHOUSE_IDS = "X-Warehouse-Ids";
    public static final String ALL_WAREHOUSES = "X-All-Warehouses";
    public static final String WAREHOUSE_ID = "X-Warehouse-Id";
    public static final String INTERNAL_KEY = "X-Internal-Key";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String TRACE_ID = "X-Trace-Id";

    /** Khóa MDC cho traceId (log pattern dùng %X{traceId}). */
    public static final String MDC_TRACE_ID = "traceId";

    private HeaderNames() {
    }
}
