package com.tickethub.payment.enums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @Test
    void initiatedCanReachAnyOutcome() {
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.FAILED));
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PENDING));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCESS));
    }

    @Test
    void finalStatesAreNotOverwritten() {
        assertFalse(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.SUCCESS.isFinal());
        assertTrue(PaymentStatus.FAILED.isFinal());
        assertFalse(PaymentStatus.PENDING.isFinal());
    }

    @Test
    void unknownIsNotFinalAndResolvesForwardOnly() {
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.UNKNOWN));
        assertFalse(PaymentStatus.UNKNOWN.isFinal());
        assertTrue(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.UNKNOWN));
    }

    @Test
    void onlyASuccessfulPaymentCanBeRefunded() {
        assertTrue(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.REFUNDED));
        assertFalse(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REFUNDED));
        assertFalse(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.SUCCESS));
    }
}
