package com.tickethub.booking.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** ST-76: /internal/** needs the shared key when one is configured, and is untouched otherwise. */
class InternalApiKeyFilterTest {

    private MockHttpServletResponse call(String configuredKey, String path, String headerValue) throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(configuredKey);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (headerValue != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // chain.getRequest() is set only when the filter let the request through
        response.setHeader("X-Passed", chain.getRequest() != null ? "yes" : "no");
        return response;
    }

    @Test
    void wrongOrMissingKeyIsRejected() throws Exception {
        assertEquals(401, call("secret", "/internal/bookings/1", null).getStatus());
        assertEquals(401, call("secret", "/internal/bookings/1", "nope").getStatus());
    }

    @Test
    void correctKeyPasses() throws Exception {
        MockHttpServletResponse r = call("secret", "/internal/bookings/1", "secret");
        assertEquals(200, r.getStatus());
        assertEquals("yes", r.getHeader("X-Passed"));
    }

    @Test
    void publicPathsAreNeverChecked() throws Exception {
        assertEquals("yes", call("secret", "/bookings", null).getHeader("X-Passed"));
    }

    @Test
    void emptyConfiguredKeyMeansOff() throws Exception {
        assertEquals("yes", call("", "/internal/bookings/1", null).getHeader("X-Passed"));
        assertNotNull(call("", "/internal/bookings/1", null));
        assertNull(call("", "/internal/bookings/1", null).getErrorMessage());
    }
}
