package com.microinvent.sales.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Chuyển lỗi {@code { error: { code, message, details } }} của service đích thành BusinessException cùng mã,
 * để client nhận đúng 409 / 400 (vd serial trùng, không đủ tồn) thay vì 500. Lỗi 5xx → SERVICE_UNAVAILABLE.
 */
public class DownstreamErrorDecoder implements ErrorDecoder {

    private final ObjectMapper mapper;

    public DownstreamErrorDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 500) {
            return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        try (InputStream body = response.body() == null ? null : response.body().asInputStream()) {
            JsonNode error = body == null ? null : mapper.readTree(body).path("error");
            String code = error == null ? "" : error.path("code").asText("");
            ErrorCode errorCode = Arrays.stream(ErrorCode.values()).filter(c -> c.name().equals(code)).findFirst()
                    .orElse(ErrorCode.BAD_REQUEST);
            Object details = error == null || error.path("details").isMissingNode() ? null
                    : mapper.convertValue(error.path("details"), Object.class);
            return new BusinessException(errorCode, error.path("message").asText(errorCode.defaultMessage()), details);
        } catch (Exception ex) {
            return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
