package com.tickethub.gateway.security;

import com.tickethub.gateway.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues (dev only) and validates HS256 JWTs. Signature and expiry are always verified. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(SecurityProperties properties) {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = properties.getJwtTtlMinutes();
    }

    public String issue(String userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttlMinutes * 60;
    }

    /** @throws io.jsonwebtoken.JwtException or IllegalArgumentException when the token is invalid or expired */
    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        List<String> roles = new ArrayList<>();
        Object raw = claims.get("roles");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                roles.add(String.valueOf(o));
            }
        }
        String email = claims.get("email", String.class);
        return new JwtPrincipal(claims.getSubject(), email, roles);
    }
}
