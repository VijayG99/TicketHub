package com.tickethub.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tickethub.gateway.config.SecurityProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenVerifierTest {

    private static final String SECRET = "0123456789-0123456789-0123456789-ab";

    private SecurityProperties props(boolean keycloakEnabled) {
        SecurityProperties p = new SecurityProperties();
        p.setJwtSecret(SECRET);
        p.getKeycloak().setEnabled(keycloakEnabled);
        // nothing listens here, so key download fails fast instead of hanging
        p.getKeycloak().setJwksUri("http://127.0.0.1:1/jwks");
        return p;
    }

    private TokenVerifier verifier(SecurityProperties p) {
        return new TokenVerifier(new JwtService(p), new KeycloakTokenVerifier(p));
    }

    private String rs256Token() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .subject("kc-user")
                .issuer("http://localhost:8180/realms/tickethub")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build());
        jwt.sign(new RSASSASigner(pair.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void gatewayIssuedTokenIsAccepted() {
        SecurityProperties p = props(false);
        String token = new JwtService(p).issue("alice", "alice@example.com", List.of("CUSTOMER"));
        JwtPrincipal principal = verifier(p).verify(token).block();
        assertEquals("alice", principal.getUserId());
        assertEquals(List.of("CUSTOMER"), principal.getRoles());
    }

    @Test
    void garbageTokenIsRejected() {
        TokenVerifier verifier = verifier(props(false));
        assertThrows(Exception.class, () -> verifier.verify("not-a-token").block());
    }

    @Test
    void keycloakTokenIsRejectedWhenKeycloakIsDisabled() throws Exception {
        String token = rs256Token();
        TokenVerifier verifier = verifier(props(false));
        assertThrows(Exception.class, () -> verifier.verify(token).block());
    }

    @Test
    void keycloakTokenIsRejectedWhenSigningKeysCannotBeFetched() throws Exception {
        String token = rs256Token();
        TokenVerifier verifier = verifier(props(true));
        assertThrows(Exception.class, () -> verifier.verify(token).block());
    }
}
