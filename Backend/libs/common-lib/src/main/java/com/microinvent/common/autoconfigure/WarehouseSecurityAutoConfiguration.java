package com.microinvent.common.autoconfigure;

import com.microinvent.common.security.RoleAccessAspect;
import com.microinvent.common.security.WarehouseAccessAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/** Bật @RequireWarehouseAccess và @RequireRoles khi có AspectJ (spring-boot-starter-aop). */
@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class WarehouseSecurityAutoConfiguration {

    @Bean
    public WarehouseAccessAspect warehouseAccessAspect() {
        return new WarehouseAccessAspect();
    }

    @Bean
    public RoleAccessAspect roleAccessAspect() {
        return new RoleAccessAspect();
    }
}
