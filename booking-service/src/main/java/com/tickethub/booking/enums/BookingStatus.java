package com.tickethub.booking.enums;

/** Booking workflow state (Blueprint status model). */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(BookingStatus target) {
        return this == PENDING_PAYMENT && target != PENDING_PAYMENT;
    }
}
