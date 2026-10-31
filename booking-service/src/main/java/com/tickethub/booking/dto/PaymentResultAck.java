package com.tickethub.booking.dto;

import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.enums.PaymentResultOutcome;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentResultAck {
    private final UUID bookingId;
    private final BookingStatus bookingStatus;
    private final PaymentResultOutcome outcome;
}
