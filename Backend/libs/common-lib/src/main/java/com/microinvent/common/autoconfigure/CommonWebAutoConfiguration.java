package com.microinvent.common.autoconfigure;

import com.microinvent.common.exception.GlobalExceptionHandler;
import com.microinvent.common.web.ContextHeaderFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Tự đăng ký filter context và xử lý lỗi chung cho mọi service servlet. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<ContextHeaderFilter> contextHeaderFilter() {
        FilterRegistrationBean<ContextHeaderFilter> bean = new FilterRegistrationBean<>(new ContextHeaderFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
