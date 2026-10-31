package com.tickethub.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class SeatLayoutRequest {
    @NotEmpty(message = "at least one row is required")
    @Valid
    private List<SeatRowRequest> rows;
}
