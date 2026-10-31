package com.tickethub.event.dto;

import com.tickethub.event.common.enums.CurrencyCode;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.ShowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Everything Booking needs to validate a show and price a booking (ST-10, ST-46). */
@Getter
@Builder
public class ShowInternalResponse {
    private final Long showId;
    private final Long eventId;
    private final String eventTitle;
    private final EventStatus eventStatus;
    private final ShowStatus showStatus;
    private final Long venueId;
    private final String venueName;
    private final Instant startTime;
    private final Instant endTime;
    private final BigDecimal ticketPrice;
    private final CurrencyCode currency;
    private final List<SeatInfo> seats;
}
