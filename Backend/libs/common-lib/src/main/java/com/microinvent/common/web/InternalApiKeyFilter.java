package com.microinvent.common.web;

import com.microinvent.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bảo vệ endpoint {@code /internal/**}: chỉ service khác (Feign) gửi đúng header X-Internal-Key mới gọi được.
 * Gateway không route /internal/** ra ngoài, filter này là lớp bảo vệ thứ hai.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_PREFIX = "/internal/";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(String apiKey) {
        this.expectedKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HeaderNames.INTERNAL_KEY);
        if (expectedKey.length == 0 || key == null
                || !MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            JsonErrorWriter.write(response, ErrorCode.INTERNAL_KEY_INVALID);
            return;
        }
        chain.doFilter(request, response);
    }
}
