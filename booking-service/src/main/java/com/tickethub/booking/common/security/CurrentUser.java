package com.tickethub.booking.common.security;

import com.tickethub.booking.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * The authenticated caller. The API Gateway validates the JWT and forwards the identity in
 * X-User-* headers (it strips any client-supplied values). Services never accept a customer id
 * from the request body.
 */
@Getter
public class CurrentUser {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private final String userId;
    private final String email;
    private final Set<Role> roles;

    public CurrentUser(String userId, String email, Set<Role> roles) {
        this.userId = userId;
        this.email = email;
        this.roles = roles;
    }

    public static CurrentUser from(HttpServletRequest request) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        String header = request.getHeader(HEADER_USER_ROLES);
        if (header != null) {
            for (String token : header.split(",")) {
                try {
                    roles.add(Role.valueOf(token.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // unknown roles are ignored
                }
            }
        }
        return new CurrentUser(request.getHeader(HEADER_USER_ID), request.getHeader(HEADER_USER_EMAIL), roles);
    }

    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public String requireUserId() {
        if (!isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }
        return userId;
    }
}
