package com.tickethub.notification.common.error;

/** Machine-readable error codes returned in the standard error response. */
public enum ErrorCode {
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    RESOURCE_NOT_FOUND,
    BUSINESS_RULE_VIOLATION,
    INVALID_STATE_TRANSITION,
    CONFLICT,
    SEAT_UNAVAILABLE,
    BOOKING_EXPIRED,
    DUPLICATE_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    SIGNATURE_INVALID,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    DEPENDENCY_UNAVAILABLE,
    DEPENDENCY_TIMEOUT,
    INTERNAL_ERROR
}
