package com.microinvent.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object details;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
