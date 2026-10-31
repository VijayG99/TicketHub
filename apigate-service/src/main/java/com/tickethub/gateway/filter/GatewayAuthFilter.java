package com.tickethub.gateway.filter;

import com.tickethub.gateway.error.ErrorCode;
import com.tickethub.gateway.error.ErrorWriter;
import com.tickethub.gateway.security.JwtPrincipal;
import com.tickethub.gateway.security.TokenVerifier;
import com.tickethub.gateway.security.PublicEndpoints;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge security and correlation:
 * - blocks /internal/** completely (service-to-service APIs are never public)
 * - validates the JWT; public browse endpoints still accept an optional valid token
 * - strips any client-supplied X-User-* headers and replaces them with the validated identity
 * - creates/propagates X-Correlation-Id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String H_USER_ID = "X-User-Id";
    private static final String H_USER_EMAIL = "X-User-Email";
    private static final String H_USER_ROLES = "X-User-Roles";
    private static final String H_CORRELATION = "X-Correlation-Id";

    private final TokenVerifier tokenVerifier;
    private final ErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        String incoming = request.getHeaders().getFirst(H_CORRELATION);
        final String correlationId = (incoming == null || incoming.isBlank() || incoming.length() > 100)
                ? UUID.randomUUID().toString() : incoming;
        exchange.getResponse().getHeaders().set(H_CORRELATION, correlationId);

        if (path.startsWith("/internal")) {
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ")) {
                return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                        "Authorization header must use the Bearer scheme");
            }
            final String token = authorization.substring(7).trim();
            // onErrorResume sits BEFORE flatMap so only token errors become 401, never downstream errors
            return tokenVerifier.verify(token)
                    .map(Optional::of)
                    .onErrorResume(e -> {
                        log.warn("Rejected invalid token for {} {}: {}", request.getMethod(), path,
                                e.getClass().getSimpleName());
                        return Mono.just(Optional.<JwtPrincipal>empty());
                    })
                    .flatMap(principal -> principal.isPresent()
                            ? forward(exchange, chain, correlationId, principal.get())
                            : errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                                    "Invalid or expired token"));
        }
        if (!PublicEndpoints.isPublic(request.getMethod(), path)) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "Authentication is required");
        }
        return forward(exchange, chain, correlationId, null);
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, String correlationId,
                               JwtPrincipal user) {
        ServerHttpRequest mutated = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(H_USER_ID);
            headers.remove(H_USER_EMAIL);
            headers.remove(H_USER_ROLES);
            headers.set(H_CORRELATION, correlationId);
            if (user != null) {
                headers.set(H_USER_ID, user.getUserId());
                if (user.getEmail() != null) {
                    headers.set(H_USER_EMAIL, user.getEmail());
                }
                headers.set(H_USER_ROLES, String.join(",", user.getRoles()));
            }
        }).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
