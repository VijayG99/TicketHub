package com.tickethub.gateway.controller;

import com.tickethub.gateway.config.SecurityProperties;
import com.tickethub.gateway.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * DEVELOPMENT-ONLY token endpoint so the stack is usable without an identity provider.
 * Disabled unless DEV_TOKEN_ENABLED=true. The target IAM architecture is Keycloak (OAuth2/OIDC):
 * services already only trust the validated identity forwarded by the gateway, so swapping the
 * issuer does not change any service.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");

    private final JwtService jwtService;
    private final SecurityProperties properties;

    @PostMapping("/token")
    public Mono<TokenResponse> token(@RequestBody TokenRequest request) {
        if (!properties.isDevTokenEnabled()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        if (request.getUserId() == null || request.getUserId().isBlank() || request.getUserId().length() > 100) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required (max 100 chars)"));
        }
        if (request.getEmail() == null || !request.getEmail().contains("@") || request.getEmail().length() > 200) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "a valid email is required"));
        }
        List<String> roles = new ArrayList<>();
        if (request.getRoles() != null) {
            for (String r : request.getRoles()) {
                String role = r == null ? "" : r.trim().toUpperCase();
                if (!ALLOWED_ROLES.contains(role)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown role: " + r));
                }
                if (!roles.contains(role)) {
                    roles.add(role);
                }
            }
        }
        if (roles.isEmpty()) {
            roles.add("CUSTOMER");
        }
        if (roles.contains("ADMIN") && !adminKeyMatches(request.getAdminKey())) {
            log.warn("Rejected ADMIN token request for user {}", request.getUserId());
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "adminKey is missing or invalid"));
        }
        String token = jwtService.issue(request.getUserId().trim(), request.getEmail().trim(), roles);
        return Mono.just(new TokenResponse(token, "Bearer", jwtService.ttlSeconds()));
    }

    private boolean adminKeyMatches(String provided) {
        String expected = properties.getDevAdminKey();
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
