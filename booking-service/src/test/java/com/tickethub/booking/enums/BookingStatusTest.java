package com.tickethub.booking.enums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BookingStatusTest {

    @Test
    void onlyPendingPaymentCanMove() {
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CONFIRMED));
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.PAYMENT_FAILED));
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.EXPIRED));
        assertTrue(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CANCELLED));
        assertFalse(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED));
        assertFalse(BookingStatus.EXPIRED.canTransitionTo(BookingStatus.CONFIRMED));
        assertFalse(BookingStatus.PAYMENT_FAILED.canTransitionTo(BookingStatus.CONFIRMED));
    }
}
