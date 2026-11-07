package com.microinvent.common.exception;

import org.springframework.http.HttpStatus;

/** Mã lỗi nghiệp vụ trả về trong error.code. Bổ sung dần theo từng service. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Tài khoản đang bị khóa tạm thời"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền"),
    WAREHOUSE_FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền trên kho này"),
    INTERNAL_KEY_INVALID(HttpStatus.FORBIDDEN, "Sai khóa nội bộ"),
    TENANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Tenant không tồn tại"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Yêu cầu đang được xử lý hoặc đã xử lý"),
    STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "Không đủ tồn kho"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Gửi quá nhiều yêu cầu, vui lòng thử lại sau"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời không khả dụng"),
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
