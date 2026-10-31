package com.tickethub.booking.exception;

import com.tickethub.booking.common.error.ErrorCode;
import com.tickethub.booking.common.exception.BusinessException;
import java.util.Collection;
import org.springframework.http.HttpStatus;

/** The loser of a seat race (or a request for an already held seat) gets a controlled 409. */
public class SeatUnavailableException extends BusinessException {

    public SeatUnavailableException(Collection<Long> seatIds) {
        super(ErrorCode.SEAT_UNAVAILABLE, HttpStatus.CONFLICT,
                "One or more selected seats are no longer available: " + seatIds);
    }
}
