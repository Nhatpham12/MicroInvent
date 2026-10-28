package com.microinvent.common.web;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Đọc header do gateway gắn vào và dựng TenantContext + UserContext cho request. */
public class ContextHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String tenant = request.getHeader(HeaderNames.TENANT_ID);
            if (StringUtils.hasText(tenant)) {
                TenantContext.set(tenant.trim());
            }
            String userId = request.getHeader(HeaderNames.USER_ID);
            if (StringUtils.hasText(userId)) {
                UserContextHolder.set(new UserContext(
                        Long.valueOf(userId),
                        request.getHeader(HeaderNames.USER_NAME),
                        split(request.getHeader(HeaderNames.USER_ROLES)),
                        split(request.getHeader(HeaderNames.WAREHOUSE_IDS)).stream().map(Long::valueOf)
                                .collect(Collectors.toUnmodifiableSet()),
                        Boolean.parseBoolean(request.getHeader(HeaderNames.ALL_WAREHOUSES)),
                        parseLong(request.getHeader(HeaderNames.WAREHOUSE_ID))));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContextHolder.clear();
        }
    }

    private static Set<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Long parseLong(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value.trim()) : null;
    }
}
