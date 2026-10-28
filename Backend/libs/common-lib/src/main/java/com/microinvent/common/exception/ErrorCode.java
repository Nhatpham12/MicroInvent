package com.microinvent.common.exception;

import org.springframework.http.HttpStatus;

/** Mã lỗi nghiệp vụ trả về trong error.code. Bổ sung dần theo từng service. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền"),
    WAREHOUSE_FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền trên kho này"),
    TENANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Tenant không tồn tại"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "Không đủ tồn kho"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
