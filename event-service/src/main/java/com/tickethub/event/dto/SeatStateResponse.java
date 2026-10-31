package com.tickethub.event.dto;

import com.tickethub.event.enums.SeatCategory;
import com.tickethub.event.enums.SeatReservationState;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatStateResponse {
    private final Long seatId;
    private final String rowLabel;
    private final int seatNumber;
    private final SeatCategory category;
    private final SeatReservationState state;
}
