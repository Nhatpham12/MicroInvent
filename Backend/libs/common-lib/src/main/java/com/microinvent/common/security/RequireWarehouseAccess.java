package com.microinvent.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kiểm tra ở server rằng người dùng có quyền trên kho đang làm việc (header X-Warehouse-Id).
 * Đặt trên method controller/service.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWarehouseAccess {

    /** true nếu thao tác ghi (nhập/xuất/chuyển...). Dành cho mở rộng quyền chỉ-xem sau này. */
    boolean write() default false;
}
