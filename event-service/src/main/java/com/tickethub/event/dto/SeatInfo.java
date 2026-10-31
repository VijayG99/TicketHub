package com.tickethub.event.dto;

import com.tickethub.event.enums.SeatCategory;
import lombok.Builder;
import lombok.Getter;

/** Seat description shared with Booking through the internal API (a DTO, never an entity). */
@Getter
@Builder
public class SeatInfo {
    private final Long seatId;
    private final String rowLabel;
    private final int seatNumber;
    private final SeatCategory category;
}
