package com.tickethub.booking.dto;

import com.tickethub.booking.common.enums.CurrencyCode;
import com.tickethub.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {
    private final UUID bookingId;
    private final Long showId;
    private final String eventTitle;
    private final String venueName;
    private final Instant showStartTime;
    private final List<BookingSeatResponse> seats;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final BookingStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant confirmedAt;
}
