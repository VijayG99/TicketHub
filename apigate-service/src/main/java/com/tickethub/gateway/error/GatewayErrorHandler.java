package com.tickethub.gateway.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Routing failures return controlled errors in the standard format (ST-12): no route -> 404,
 * no healthy instance / connection refused -> 503, upstream too slow -> 504. No internals leak.
 */
@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ErrorWriter errorWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            int code = rse.getStatusCode().value();
            switch (code) {
                case 400:
                    return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, safe(rse, "Bad request"));
                case 401:
                    return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, safe(rse, "Authentication required"));
                case 403:
                    return errorWriter.write(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, safe(rse, "Forbidden"));
                case 404:
                    return errorWriter.write(exchange, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
                case 405:
                    return errorWriter.write(exchange, HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed");
                case 503:
                    log.warn("Upstream unavailable for {}: {}", exchange.getRequest().getURI().getPath(), rse.getReason());
                    return unavailable(exchange);
                case 504:
                    return timeout(exchange);
                default:
                    break;
            }
        }
        if (hasCause(ex, "ConnectException") || hasCause(ex, "UnknownHostException")) {
            log.warn("Upstream connection failed for {}: {}", exchange.getRequest().getURI().getPath(), ex.toString());
            return unavailable(exchange);
        }
        if (hasCause(ex, "TimeoutException")) {
            log.warn("Upstream timeout for {}: {}", exchange.getRequest().getURI().getPath(), ex.toString());
            return timeout(exchange);
        }
        log.error("Unexpected gateway failure for {}", exchange.getRequest().getURI().getPath(), ex);
        return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the correlationId when contacting support.");
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE,
                "The requested service is temporarily unavailable. Please try again shortly.");
    }

    private Mono<Void> timeout(ServerWebExchange exchange) {
        return errorWriter.write(exchange, HttpStatus.GATEWAY_TIMEOUT, ErrorCode.DEPENDENCY_TIMEOUT,
                "The requested service did not respond in time.");
    }

    private String safe(ResponseStatusException e, String fallback) {
        return e.getReason() != null ? e.getReason() : fallback;
    }

    private boolean hasCause(Throwable t, String simpleNameFragment) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getClass().getSimpleName().contains(simpleNameFragment)) {
                return true;
            }
        }
        return false;
    }
}
