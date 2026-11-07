package com.microinvent.common.security;

import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.JsonErrorWriter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Trả 401/403 theo khuôn ApiResponse thay vì trang lỗi mặc định của Spring Security. */
public final class JsonAuthErrorHandlers {

    private JsonAuthErrorHandlers() {
    }

    public static AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> JsonErrorWriter.write(res, ErrorCode.UNAUTHORIZED);
    }

    public static AccessDeniedHandler accessDenied() {
        return (req, res, ex) -> JsonErrorWriter.write(res, ErrorCode.FORBIDDEN);
    }
}
