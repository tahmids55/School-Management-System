package com.school.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void doFilter_whenHeaderMissing_generatesUuidAndClearsMdcAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInsideFilter = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcInsideFilter.set(MDC.get(RequestIdFilter.MDC_KEY)));

        String header = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header)).isNotNull();
        assertThat(mdcInsideFilter.get()).isEqualTo(header);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilter_whenHeaderPresent_reusesAndCapsAt64() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        String longId = "req-" + "x".repeat(80);
        request.addHeader(RequestIdFilter.HEADER_NAME, longId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(header).hasSize(64);
        assertThat(header).isEqualTo(longId.substring(0, 64));
    }

    @Test
    void doFilter_whenHeaderContainsControlChars_generatesNewUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header)).isNotNull();
    }
}
