package com.microinvent.procurement.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownstreamErrorDecoderTest {

    private final DownstreamErrorDecoder decoder = new DownstreamErrorDecoder(new ObjectMapper());

    @Test
    void keepsDownstreamBusinessCodeMessageAndDetails() {
        BusinessException ex = (BusinessException) decoder.decode("x", response(409,
                "{\"success\":false,\"error\":{\"code\":\"CONFLICT\",\"message\":\"Serial đã tồn tại trong hệ thống\","
                        + "\"details\":{\"serials\":[\"SN-1\"]}}}"));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Serial đã tồn tại trong hệ thống");
        assertThat(ex.getDetails()).isEqualTo(Map.of("serials", List.of("SN-1")));
    }

    @Test
    void serverErrorsAndUnreadableBodiesBecomeServiceUnavailable() {
        assertThat(((BusinessException) decoder.decode("x", response(500, "boom"))).getCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(((BusinessException) decoder.decode("x", response(400, "not json"))).getCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private static Response response(int status, String body) {
        Request req = Request.create(Request.HttpMethod.POST, "http://inventory/internal/stock/receive", Map.of(),
                null, StandardCharsets.UTF_8, null);
        return Response.builder().status(status).request(req).body(body, StandardCharsets.UTF_8).headers(Map.of())
                .build();
    }
}
