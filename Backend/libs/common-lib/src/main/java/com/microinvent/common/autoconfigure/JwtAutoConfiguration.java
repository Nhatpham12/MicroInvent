package com.microinvent.common.autoconfigure;

import com.microinvent.common.security.JwtProperties;
import com.microinvent.common.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Tạo JwtService khi có jjwt trên classpath và đã cấu hình microinvent.jwt.secret. */
@AutoConfiguration
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
@ConditionalOnProperty(prefix = "microinvent.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties props) {
        return new JwtService(props);
    }
}
