package com.tickethub.event.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatAvailabilityResponse {
    private final Long showId;
    private final List<SeatStateResponse> seats;
}
