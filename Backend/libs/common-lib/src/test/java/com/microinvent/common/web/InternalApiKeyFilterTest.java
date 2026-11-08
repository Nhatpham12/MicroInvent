package com.microinvent.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiKeyFilterTest {

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter("secret-key");

    private MockHttpServletResponse call(String uri, String key) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        if (key != null) {
            req.addHeader(HeaderNames.INTERNAL_KEY, key);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void internalPathRequiresCorrectKey() throws Exception {
        assertThat(call("/internal/users/1/scope", null).getStatus()).isEqualTo(403);
        assertThat(call("/internal/users/1/scope", "wrong").getStatus()).isEqualTo(403);
        assertThat(call("/internal/users/1/scope", "secret-key").getStatus()).isEqualTo(200);
    }

    @Test
    void publicPathIsNotAffected() throws Exception {
        assertThat(call("/api/v1/products", null).getStatus()).isEqualTo(200);
    }
}
