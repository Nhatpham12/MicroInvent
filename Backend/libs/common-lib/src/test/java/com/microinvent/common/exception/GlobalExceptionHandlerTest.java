package com.microinvent.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.microinvent.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionKeepsItsCode() {
        ResponseEntity<ApiResponse<Void>> res = handler
                .handleBusiness(new BusinessException(ErrorCode.TENANT_NOT_FOUND, "Tenant không tồn tại: abc"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void wrappedBusinessExceptionIsUnwrapped() {
        BusinessException cause = new BusinessException(ErrorCode.TENANT_NOT_FOUND, "Tenant không tồn tại: abc");
        Exception wrapped = new CannotCreateTransactionException("Could not open JPA EntityManager", cause);

        ResponseEntity<ApiResponse<Void>> res = handler.handleOther(wrapped);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().error().code()).isEqualTo("TENANT_NOT_FOUND");
        assertThat(res.getBody().error().message()).isEqualTo("Tenant không tồn tại: abc");
    }

    @Test
    void unknownExceptionIs500WithoutLeakingMessage() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleOther(new IllegalStateException("chi tiết nội bộ"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(res.getBody().error().message()).doesNotContain("nội bộ");
    }
}
