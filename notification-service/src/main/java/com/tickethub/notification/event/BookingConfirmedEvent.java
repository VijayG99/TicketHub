package com.tickethub.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification's own view of the BookingConfirmed contract published by Booking (ST-19).
 *
 * <h3>Contract versioning (Phase 2, S13)</h3>
 * <ul>
 *   <li><b>v1</b> had {@code amount}.</li>
 *   <li><b>v2</b> ADDS {@code totalAmount} and {@code seatCategory} and still sends {@code amount}
 *       (the "expand" step). Old consumers keep working because they only read {@code amount}.</li>
 *   <li>When every consumer reads {@code totalAmount}, Booking can stop sending {@code amount}
 *       (the "contract" step).</li>
 * </ul>
 * {@code ignoreUnknown = true} is the other half of the rule: a consumer must IGNORE fields it does
 * not know, so a newer producer never breaks it.
 * This class reads BOTH versions: see {@link #effectiveAmount()}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingConfirmedEvent {
    private UUID eventId;
    private String eventType;
    /** Missing on very old messages: treat as 1. */
    private Integer eventVersion;
    /** Per-booking counter (S6). Used to ignore stale/out-of-order events. */
    private Long aggregateVersion;
    private Instant occurredAt;
    private UUID bookingId;
    private String customerId;
    private String customerEmail;
    private Long showId;
    private String eventTitle;
    private String venueName;
    private Instant showStartTime;
    private List<Long> seatIds;
    private List<String> seatLabels;
    /** v1 field. */
    private BigDecimal amount;
    /** v2 field (S13). */
    private BigDecimal totalAmount;
    /** v2 field (S13). Optional. */
    private String seatCategory;
    private String currency;

    /** Works for v1 (only amount) and v2 (totalAmount, with amount as a fallback). */
    public BigDecimal effectiveAmount() {
        return totalAmount != null ? totalAmount : amount;
    }
}
