package com.tickethub.payment.enums;

/**
 * Payment lifecycle. Only FORWARD moves are allowed (S9), so a late or repeated provider
 * callback can never move a payment backwards.
 *
 * <pre>
 *   INITIATED --> PENDING ----------> SUCCESS --(booking rejected/cancelled)--> REFUNDED
 *       |            |   \               ^
 *       |            |    +--> UNKNOWN ---+--> FAILED / CANCELLED
 *       +------------+---> FAILED / CANCELLED
 *
 *   UNKNOWN = we called the provider and it did not answer in time. The customer MAY have been charged.
 *             We never guess: the reconciliation job asks the provider what really happened.
 * </pre>
 */
public enum PaymentStatus {
    INITIATED,
    PENDING,
    UNKNOWN,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        switch (this) {
            case INITIATED:
                return target == PENDING || target == UNKNOWN || target == SUCCESS || target == FAILED || target == CANCELLED;
            case PENDING:
                return target == UNKNOWN || target == SUCCESS || target == FAILED || target == CANCELLED;
            case UNKNOWN:
                return target == PENDING || target == SUCCESS || target == FAILED || target == CANCELLED;
            case SUCCESS:
                return target == REFUNDED;
            default:
                return false;
        }
    }

    /** No further provider outcome may change a final payment. UNKNOWN is deliberately NOT final. */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == REFUNDED;
    }
}
