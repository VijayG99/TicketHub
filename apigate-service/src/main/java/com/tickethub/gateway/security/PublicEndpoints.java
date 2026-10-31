package com.tickethub.gateway.security;

import org.springframework.http.HttpMethod;

/** Endpoints reachable without a token. Everything else requires a valid JWT. */
public final class PublicEndpoints {

    private PublicEndpoints() {
    }

    public static boolean isPublic(HttpMethod method, String path) {
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        if (HttpMethod.POST.equals(method) && path.equals("/payments/webhook")) {
            return true; // authenticated by HMAC signature inside Payment Service
        }
        if (HttpMethod.GET.equals(method)) {
            return path.equals("/events") || path.startsWith("/events/")
                    || path.equals("/shows") || path.startsWith("/shows/");
        }
        return false;
    }
}
