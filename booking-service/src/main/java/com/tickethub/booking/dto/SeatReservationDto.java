package com.tickethub.booking.dto;

import com.tickethub.booking.enums.SeatReservationState;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Seat state as exposed to Event/Show: only RESERVED/BOOKED seats are listed; the rest are AVAILABLE. */
@Getter
@AllArgsConstructor
public class SeatReservationDto {
    private final Long seatId;
    private final SeatReservationState state;
}
