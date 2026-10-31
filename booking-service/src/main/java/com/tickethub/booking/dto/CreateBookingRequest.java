package com.tickethub.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Note: no customerId and no amount. Identity comes from the token; price is computed (ST-46). */
@Data
public class CreateBookingRequest {
    @NotNull(message = "showId is required")
    private Long showId;

    @NotEmpty(message = "at least one seat is required")
    @Size(max = 20, message = "too many seats in one request")
    private List<@NotNull(message = "seatIds must not contain null") Long> seatIds;
}
