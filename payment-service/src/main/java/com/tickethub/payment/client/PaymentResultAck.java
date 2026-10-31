package com.tickethub.payment.client;

import com.tickethub.payment.enums.BookingStatus;
import com.tickethub.payment.enums.PaymentResultOutcome;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaymentResultAck {
    private UUID bookingId;
    private BookingStatus bookingStatus;
    private PaymentResultOutcome outcome;
}
