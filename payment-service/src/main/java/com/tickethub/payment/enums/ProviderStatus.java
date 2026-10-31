package com.tickethub.payment.enums;

/** Provider-side outcome; mapped to {@link PaymentStatus} inside Payment Service only. */
public enum ProviderStatus {
    SUCCEEDED,
    FAILED,
    PENDING
}
