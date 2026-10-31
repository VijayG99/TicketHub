package com.tickethub.gateway.error;

/** Same codes as the services' standard error contract. */
public enum ErrorCode {
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    RESOURCE_NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    METHOD_NOT_ALLOWED,
    DEPENDENCY_UNAVAILABLE,
    DEPENDENCY_TIMEOUT,
    INTERNAL_ERROR
}
