package com.tickethub.gateway.security;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JwtPrincipal {
    private final String userId;
    private final String email;
    private final List<String> roles;
}
