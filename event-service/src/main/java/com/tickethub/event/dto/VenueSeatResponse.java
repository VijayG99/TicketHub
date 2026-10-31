package com.tickethub.event.dto;

import com.tickethub.event.enums.SeatCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VenueSeatResponse {
    private final Long seatId;
    private final String rowLabel;
    private final int seatNumber;
    private final SeatCategory category;
}
