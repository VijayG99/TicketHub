package com.tickethub.payment.event;

import com.tickethub.payment.common.enums.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Events Payment sends to Booking on topic "payment-events" (key = bookingId).
 *   PaymentSucceeded (aggregateVersion 1), PaymentFailed (1), PaymentRefunded (2).
 * The aggregateVersion lets Booking ignore an older event that arrives after a newer one (S6).
 */
@Getter
@Builder
public class PaymentSagaEvent {
    private final UUID eventId;
    private final String eventType;
    private final int eventVersion;
    private final long aggregateVersion;
    private final Instant occurredAt;
    private final UUID bookingId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final String reason;
}
