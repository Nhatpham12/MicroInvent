package com.microinvent.common.autoconfigure;

import com.microinvent.common.security.WarehouseAccessAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class WarehouseSecurityAutoConfiguration {

    @Bean
    public WarehouseAccessAspect warehouseAccessAspect() {
        return new WarehouseAccessAspect();
    }
}
