package com.tickethub.booking.event;

import com.tickethub.booking.common.enums.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Events Booking sends to Payment on topic "booking-events" (key = bookingId):
 *   BookingRejected  - payment arrived but the booking cannot be confirmed (expired/cancelled) => refund (S5)
 *   BookingCancelled - the customer cancelled a CONFIRMED booking => refund (S5)
 * aggregateVersion orders them against other events of the same booking (S6).
 */
@Getter
@Builder
public class BookingSagaEvent {
    private final UUID eventId;
    private final String eventType;
    private final int eventVersion;
    private final long aggregateVersion;
    private final Instant occurredAt;
    private final UUID bookingId;
    private final String customerId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final String reason;
}
