package com.tickethub.event.dto;

import com.tickethub.event.common.enums.CurrencyCode;
import com.tickethub.event.enums.ShowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShowResponse {
    private final Long id;
    private final Long eventId;
    private final String eventTitle;
    private final Long venueId;
    private final String venueName;
    private final Instant startTime;
    private final Instant endTime;
    private final BigDecimal ticketPrice;
    private final CurrencyCode currency;
    private final ShowStatus status;
}
