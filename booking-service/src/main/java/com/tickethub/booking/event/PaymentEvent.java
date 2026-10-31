package com.tickethub.booking.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Events Payment sends to Booking on topic "payment-events" (key = bookingId):
 *   PaymentSucceeded, PaymentFailed (version 1), PaymentRefunded (version 2).
 * Booking's own view of the contract; unknown fields are ignored (S13).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent {
    private UUID eventId;
    private String eventType;
    private Integer eventVersion;
    private Long aggregateVersion;
    private Instant occurredAt;
    private UUID bookingId;
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
    private String reason;
}
