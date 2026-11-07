package com.microinvent.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.context.TenantContext;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Chống xử lý trùng theo header Idempotency-Key (Redis). Lần đầu: đánh dấu PROCESSING, chạy action, lưu kết quả.
 * Gọi lại cùng key: trả lại kết quả đã lưu; nếu lần trước còn đang chạy thì báo DUPLICATE_REQUEST.
 * Action lỗi thì xóa key để client thử lại được.
 *
 * <pre>{@code
 * return idempotency.execute("receipt.confirm", idempotencyKey, ReceiptDto.class, () -> service.confirm(id));
 * }</pre>
 */
public class IdempotencyService {

    static final String PROCESSING = "__PROCESSING__";
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public <T> T execute(String scope, String key, Class<T> type, Supplier<T> action) {
        return execute(scope, key, DEFAULT_TTL, type, action);
    }

    public <T> T execute(String scope, String key, Duration ttl, Class<T> type, Supplier<T> action) {
        if (!StringUtils.hasText(key)) {
            return action.get();
        }
        String redisKey = redisKey(scope, key);
        Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, PROCESSING, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            String stored = redis.opsForValue().get(redisKey);
            if (stored == null || PROCESSING.equals(stored)) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }
            return read(stored, type);
        }
        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            redis.delete(redisKey);
            throw ex;
        }
        redis.opsForValue().set(redisKey, write(result), ttl);
        return result;
    }

    static String redisKey(String scope, String key) {
        String tenant = TenantContext.get();
        return "idem:" + (tenant == null ? "_" : tenant) + ":" + scope + ":" + key;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được kết quả idempotency");
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không lưu được kết quả idempotency");
        }
    }
}
