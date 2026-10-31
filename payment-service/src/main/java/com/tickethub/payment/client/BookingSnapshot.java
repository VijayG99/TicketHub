package com.tickethub.payment.client;

import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payment's own view of Booking's internal contract. */
@Data
@NoArgsConstructor
public class BookingSnapshot {
    private UUID bookingId;
    private String customerId;
    private Long showId;
    private BigDecimal amount;
    private CurrencyCode currency;
    private BookingStatus status;
    private Instant expiresAt;
}
