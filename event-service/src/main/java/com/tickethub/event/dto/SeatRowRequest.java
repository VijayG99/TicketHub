package com.tickethub.event.dto;

import com.tickethub.event.enums.SeatCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SeatRowRequest {
    @NotNull(message = "rowLabel is required")
    @Pattern(regexp = "[A-Z]{1,2}", message = "rowLabel must be 1-2 uppercase letters")
    private String rowLabel;

    @Min(value = 1, message = "seatCount must be at least 1")
    @Max(value = 100, message = "seatCount must be at most 100")
    private int seatCount;

    @NotNull(message = "category is required")
    private SeatCategory category;
}
