package com.tickethub.booking.enums;

/** What Booking did with a reported payment result. Makes the callback idempotent and auditable. */
public enum PaymentResultOutcome {
    APPLIED,
    IGNORED_DUPLICATE,
    REJECTED_EXPIRED,
    REJECTED_INVALID_STATE
}
