package com.tickethub.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {
    private final String token;
    private final String tokenType;
    private final long expiresInSeconds;
}
