package com.tickethub.booking.enums;

/**
 * Where a booking is in the payment saga (S7). This is separate from {@link BookingStatus}:
 * status says what the CUSTOMER sees, saga state says what the SYSTEM is doing about money.
 *
 * <pre>
 *   PAYMENT_PENDING --PaymentSucceeded--> CONFIRMED --customer cancels--> COMPENSATING --PaymentRefunded--> COMPENSATED
 *        |                                                                    ^
 *        |--PaymentSucceeded but booking expired/cancelled (too late)---------+   (BookingRejected sent)
 *        |
 *        +--PaymentFailed / expiry / customer cancel before paying--> FAILED   (no money moved, nothing to undo)
 * </pre>
 */
public enum SagaState {
    PAYMENT_PENDING,
    CONFIRMED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
