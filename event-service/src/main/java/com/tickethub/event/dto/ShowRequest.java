package com.tickethub.event.dto;

import com.tickethub.event.common.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class ShowRequest {
    @NotNull(message = "eventId is required")
    private Long eventId;

    @NotNull(message = "venueId is required")
    private Long venueId;

    @NotNull(message = "startTime is required")
    private Instant startTime;

    @NotNull(message = "endTime is required")
    private Instant endTime;

    @NotNull(message = "ticketPrice is required")
    @DecimalMin(value = "0.01", message = "ticketPrice must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "ticketPrice allows up to 10 integer and 2 fraction digits")
    private BigDecimal ticketPrice;

    @NotNull(message = "currency is required")
    private CurrencyCode currency;
}
