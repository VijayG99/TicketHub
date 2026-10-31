package com.tickethub.booking.event;

import com.tickethub.booking.common.enums.CurrencyCode;
import com.tickethub.booking.enums.SeatCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * BookingConfirmed event contract. Topic: booking-confirmed, key: bookingId, JSON value.
 *
 * <h3>Versions (S13)</h3>
 * <ul>
 *   <li>v1: had {@code amount}.</li>
 *   <li>v2 (now): ADDS {@code totalAmount} and {@code seatCategory}, and STILL sends {@code amount}
 *       so consumers that only know v1 keep working ("expand" step of expand/contract).</li>
 * </ul>
 * eventId is derived from the bookingId, so the same confirmation always has the same id and
 * consumers can detect duplicates (inbox, S2).
 * aggregateVersion is a per-booking counter (S6) so a consumer can ignore a stale event.
 */
@Getter
@Builder
public class BookingConfirmedEvent {
    private final UUID eventId;
    private final String eventType;
    private final int eventVersion;
    private final long aggregateVersion;
    private final Instant occurredAt;
    private final UUID bookingId;
    private final String customerId;
    private final String customerEmail;
    private final Long showId;
    private final String eventTitle;
    private final String venueName;
    private final Instant showStartTime;
    private final List<Long> seatIds;
    private final List<String> seatLabels;
    /** v1 field, kept for old consumers. */
    private final BigDecimal amount;
    /** v2 field: same value as amount. */
    private final BigDecimal totalAmount;
    /** v2 field: category of the first seat (optional for consumers). */
    private final SeatCategory seatCategory;
    private final CurrencyCode currency;
}
