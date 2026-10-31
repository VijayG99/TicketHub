package com.tickethub.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Single entry point for token validation. RS256 tokens go to Keycloak verification (when enabled),
 * everything else is checked as a gateway-issued HS256 development token.
 */
@Component
@RequiredArgsConstructor
public class TokenVerifier {

    private final JwtService jwtService;
    private final KeycloakTokenVerifier keycloak;

    public Mono<JwtPrincipal> verify(String token) {
        if (keycloak.isEnabled() && isRs256(token)) {
            // key download is blocking I/O: keep it off the event loop
            return Mono.fromCallable(() -> keycloak.verify(token)).subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.fromCallable(() -> jwtService.parse(token));
    }

    private boolean isRs256(String token) {
        try {
            return JWSAlgorithm.RS256.equals(SignedJWT.parse(token).getHeader().getAlgorithm());
        } catch (Exception e) {
            return false;
        }
    }
}
