package com.tickethub.booking.dto;

import com.tickethub.booking.common.enums.CurrencyCode;
import com.tickethub.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** What Payment needs: the amount snapshot, ownership and whether the booking is still payable. */
@Getter
@Builder
public class BookingInternalResponse {
    private final UUID bookingId;
    private final String customerId;
    private final Long showId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final BookingStatus status;
    private final Instant expiresAt;
}
