package com.microinvent.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyServiceTest {

    record Result(String code, int qty) {
    }

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private IdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new IdempotencyService(redis, new ObjectMapper());
    }

    @Test
    void firstCallRunsActionAndStoresResult() {
        when(ops.setIfAbsent(anyString(), eq(IdempotencyService.PROCESSING), any(Duration.class))).thenReturn(true);

        Result r = service.execute("receipt.confirm", "k1", Result.class, () -> new Result("PN001", 3));

        assertThat(r).isEqualTo(new Result("PN001", 3));
        verify(ops).set(eq("idem:_:receipt.confirm:k1"), eq("{\"code\":\"PN001\",\"qty\":3}"), any(Duration.class));
    }

    @Test
    void repeatedCallReturnsStoredResultWithoutRunningAction() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get("idem:_:receipt.confirm:k1")).thenReturn("{\"code\":\"PN001\",\"qty\":3}");
        AtomicInteger calls = new AtomicInteger();

        Result r = service.execute("receipt.confirm", "k1", Result.class, () -> {
            calls.incrementAndGet();
            return new Result("X", 0);
        });

        assertThat(r).isEqualTo(new Result("PN001", 3));
        assertThat(calls).hasValue(0);
    }

    @Test
    void concurrentCallWhileProcessingIsRejected() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(anyString())).thenReturn(IdempotencyService.PROCESSING);

        assertThatThrownBy(() -> service.execute("s", "k", Result.class, () -> new Result("X", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    void failedActionReleasesKey() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.execute("s", "k", Result.class, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(redis).delete("idem:_:s:k");
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blankKeyJustRunsAction() {
        Result r = service.execute("s", " ", Result.class, () -> new Result("A", 1));
        assertThat(r.code()).isEqualTo("A");
        verify(redis, never()).opsForValue();
    }
}
