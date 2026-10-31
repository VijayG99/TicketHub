package com.tickethub.event.client;

import com.tickethub.event.enums.SeatReservationState;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Booking's seat-state contract, deserialized with Event/Show's own DTO. */
@Data
@NoArgsConstructor
public class SeatReservationDto {
    private Long seatId;
    private SeatReservationState state;
}
