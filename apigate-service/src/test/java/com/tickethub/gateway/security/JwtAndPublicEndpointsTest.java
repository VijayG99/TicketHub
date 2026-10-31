package com.tickethub.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickethub.gateway.config.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class JwtAndPublicEndpointsTest {

    private JwtService service(String secret) {
        SecurityProperties p = new SecurityProperties();
        p.setJwtSecret(secret);
        return new JwtService(p);
    }

    @Test
    void issuedTokenRoundTrips() {
        JwtService jwt = service("0123456789-0123456789-0123456789-ab");
        String token = jwt.issue("alice", "alice@example.com", List.of("CUSTOMER"));
        JwtPrincipal principal = jwt.parse(token);
        assertEquals("alice", principal.getUserId());
        assertEquals("alice@example.com", principal.getEmail());
        assertEquals(List.of("CUSTOMER"), principal.getRoles());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("0123456789-0123456789-0123456789-ab").issue("alice", "a@b.c", List.of("CUSTOMER"));
        JwtService other = service("ZZZZZZZZZZ-ZZZZZZZZZZ-ZZZZZZZZZZ-ab");
        assertThrows(Exception.class, () -> other.parse(token));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> service("too-short"));
    }

    @Test
    void publicEndpointRules() {
        assertTrue(PublicEndpoints.isPublic(HttpMethod.GET, "/events"));
        assertTrue(PublicEndpoints.isPublic(HttpMethod.GET, "/shows/5/seats"));
        assertTrue(PublicEndpoints.isPublic(HttpMethod.POST, "/payments/webhook"));
        assertFalse(PublicEndpoints.isPublic(HttpMethod.POST, "/events"));
        assertFalse(PublicEndpoints.isPublic(HttpMethod.POST, "/bookings"));
        assertFalse(PublicEndpoints.isPublic(HttpMethod.GET, "/venues"));
        assertFalse(PublicEndpoints.isPublic(HttpMethod.GET, "/notifications"));
    }
}
