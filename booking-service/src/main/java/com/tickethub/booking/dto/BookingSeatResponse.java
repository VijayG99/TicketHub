package com.tickethub.booking.dto;

import com.tickethub.booking.enums.SeatCategory;
import com.tickethub.booking.enums.SeatReservationState;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingSeatResponse {
    private final Long seatId;
    private final String label;
    private final String rowLabel;
    private final int seatNumber;
    private final SeatCategory category;
    private final SeatReservationState state;
}
