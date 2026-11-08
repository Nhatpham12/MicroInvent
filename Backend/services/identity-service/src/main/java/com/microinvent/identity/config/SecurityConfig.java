package com.microinvent.identity.config;

import com.microinvent.common.security.GatewayHeaderAuthenticationFilter;
import com.microinvent.common.security.JsonAuthErrorHandlers;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT được api-gateway xác thực; service nhận người dùng qua header X-User-* (ContextHeaderFilter của common-lib)
 * và GatewayHeaderAuthenticationFilter chuyển thành Authentication (ROLE_ADMIN, ROLE_MANAGER...).
 * /internal/** được bảo vệ bằng X-Internal-Key (InternalApiKeyFilter).
 */
@Configuration
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password", "/api/v1/auth/verify-otp",
        "/api/v1/auth/reset-password", "/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**",
        "/swagger-ui/**", "/swagger-ui.html", "/internal/**", "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(JsonAuthErrorHandlers.entryPoint())
                        .accessDeniedHandler(JsonAuthErrorHandlers.accessDenied()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/users/**", "/api/v1/roles/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
