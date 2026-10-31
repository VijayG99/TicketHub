package com.tickethub.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.tickethub.gateway.config.SecurityProperties;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Verifies RS256 access tokens issued by Keycloak (ST-15). The signature is checked against
 * Keycloak's published keys (JWKS, cached and refreshed automatically), then expiry and issuer.
 * Realm roles ADMIN / CUSTOMER from "realm_access.roles" become the TicketHub roles.
 * The keys are downloaded lazily, so the gateway starts even when Keycloak is not running.
 */
@Component
public class KeycloakTokenVerifier {

    private final SecurityProperties.Keycloak settings;
    private volatile DefaultJWTProcessor<SecurityContext> processor;

    public KeycloakTokenVerifier(SecurityProperties properties) {
        this.settings = properties.getKeycloak();
    }

    public boolean isEnabled() {
        return settings.isEnabled();
    }

    /** @throws Exception when the signature, expiry or issuer is invalid */
    public JwtPrincipal verify(String token) throws Exception {
        JWTClaimsSet claims = processor().process(token, null);
        if (!settings.getIssuer().equals(claims.getIssuer())) {
            throw new BadJWTException("Unexpected token issuer");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new BadJWTException("Token has no subject");
        }
        List<String> roles = new ArrayList<>();
        Map<String, Object> realmAccess = claims.getJSONObjectClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List) {
            for (Object o : (List<?>) realmAccess.get("roles")) {
                String role = String.valueOf(o).toUpperCase(Locale.ROOT);
                if (("ADMIN".equals(role) || "CUSTOMER".equals(role)) && !roles.contains(role)) {
                    roles.add(role);
                }
            }
        }
        if (roles.isEmpty()) {
            roles.add("CUSTOMER");
        }
        return new JwtPrincipal(claims.getSubject(), claims.getStringClaim("email"), roles);
    }

    private DefaultJWTProcessor<SecurityContext> processor() throws Exception {
        DefaultJWTProcessor<SecurityContext> p = processor;
        if (p == null) {
            synchronized (this) {
                p = processor;
                if (p == null) {
                    JWKSource<SecurityContext> keys = JWKSourceBuilder.<SecurityContext>create(
                            new URL(settings.getJwksUri()), new DefaultResourceRetriever(2000, 2000)).build();
                    p = new DefaultJWTProcessor<>();
                    p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
                    processor = p;
                }
            }
        }
        return p;
    }
}
