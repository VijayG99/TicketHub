package com.tickethub.booking.client;

import com.tickethub.booking.common.enums.CurrencyCode;
import com.tickethub.booking.enums.EventStatus;
import com.tickethub.booking.enums.SeatCategory;
import com.tickethub.booking.enums.ShowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Booking's own view of Event/Show's internal show contract (ST-10: no shared entity classes). */
@Data
@NoArgsConstructor
public class ShowInfo {
    private Long showId;
    private Long eventId;
    private String eventTitle;
    private EventStatus eventStatus;
    private ShowStatus showStatus;
    private Long venueId;
    private String venueName;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal ticketPrice;
    private CurrencyCode currency;
    private List<SeatInfo> seats;

    @Data
    @NoArgsConstructor
    public static class SeatInfo {
        private Long seatId;
        private String rowLabel;
        private int seatNumber;
        private SeatCategory category;
    }
}
