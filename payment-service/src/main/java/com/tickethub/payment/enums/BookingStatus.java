package com.tickethub.payment.enums;

/** Booking service contract value (mirrors its status model; no shared classes). */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    EXPIRED
}
