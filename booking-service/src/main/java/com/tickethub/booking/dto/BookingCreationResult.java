package com.tickethub.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** replayed = true when an idempotent retry returned the original booking (HTTP 200 instead of 201). */
@Getter
@AllArgsConstructor
public class BookingCreationResult {
    private final BookingResponse booking;
    private final boolean replayed;
}
