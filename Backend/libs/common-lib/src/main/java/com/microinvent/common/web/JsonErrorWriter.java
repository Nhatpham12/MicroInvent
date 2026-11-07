package com.microinvent.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/** Ghi lỗi theo khuôn ApiResponse từ filter (ngoài phạm vi @RestControllerAdvice). */
public final class JsonErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(res.getOutputStream(),
                ApiResponse.fail(new ApiError(code.name(), code.defaultMessage(), null)));
    }
}
