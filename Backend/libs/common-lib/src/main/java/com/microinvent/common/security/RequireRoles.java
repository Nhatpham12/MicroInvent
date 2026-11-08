package com.microinvent.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chỉ cho phép người dùng có ít nhất một trong các vai trò (ADMIN, MANAGER, ACCOUNTANT, STOREKEEPER, SALES).
 * Không khai báo vai trò nào = chỉ cần đăng nhập. Dùng được cho service không có Spring Security.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    String[] value() default {};
}
