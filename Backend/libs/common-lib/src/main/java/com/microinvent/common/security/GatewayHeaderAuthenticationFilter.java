package com.microinvent.common.security;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cho service có Spring Security: chuyển UserContext (do gateway gắn qua header X-User-*) thành
 * Authentication với authority ROLE_&lt;code&gt;, để dùng được authorizeHttpRequests / @PreAuthorize.
 * Thêm vào chain: {@code http.addFilterBefore(new GatewayHeaderAuthenticationFilter(),
 * UsernamePasswordAuthenticationFilter.class)}.
 */
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UserContext user = UserContextHolder.get();
        if (user.userId() != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<SimpleGrantedAuthority> authorities = user.roles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        }
        chain.doFilter(request, response);
    }
}
