package com.tickethub.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tickethub.security")
public class SecurityProperties {
    /** HS256 signing secret (at least 32 bytes). Supplied through the JWT_SECRET environment variable. */
    private String jwtSecret;
    private long jwtTtlMinutes = 60;
    /** Enables POST /auth/token. Development only: Keycloak replaces this as the identity provider. */
    private boolean devTokenEnabled = false;
    /** Required to mint ADMIN tokens from the dev token endpoint. */
    private String devAdminKey = "";
    /** Keycloak (OAuth2/OIDC) token verification, optional (ST-15). */
    private Keycloak keycloak = new Keycloak();

    @Data
    public static class Keycloak {
        /** When true the gateway also accepts RS256 access tokens issued by Keycloak. */
        private boolean enabled = false;
        /** Where the gateway (inside Docker) downloads Keycloak's public signing keys. */
        private String jwksUri = "http://keycloak:8080/realms/tickethub/protocol/openid-connect/certs";
        /** The "iss" claim every Keycloak token must carry (the URL clients used to obtain it). */
        private String issuer = "http://localhost:8180/realms/tickethub";
    }
}
