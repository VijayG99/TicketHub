package com.tickethub.event.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ST-76 (advanced IAM pattern): SERVICE-TO-SERVICE authentication.
 *
 * Until now /internal/** was protected only because the gateway does not route it and the service ports
 * are not published (network isolation). That is one layer. This filter adds a second one: every call to
 * /internal/** must carry the shared secret header X-Internal-Key, otherwise it gets 401.
 *
 * Switched on by setting INTERNAL_API_KEY in .env (passed to the service as
 * TICKETHUB_SECURITY_INTERNAL_API_KEY). Empty (the default) = filter does nothing, so the stack still
 * works unchanged. The comparison is constant-time. The next step up (not built) is per-service
 * identities with short-lived OAuth2 client-credentials tokens from Keycloak, see docs/KEYCLOAK.md.
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";

    private final byte[] expected;

    public InternalApiKeyFilter(@Value("${tickethub.security.internal-api-key:}") String key) {
        this.expected = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
        log.info("Internal API key protection for /internal/** is {}", expected == null ? "OFF" : "ON");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected == null || !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given != null && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected {} {} : missing or wrong {}", request.getMethod(), request.getRequestURI(), HEADER);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Internal API key required\"}");
    }
}
