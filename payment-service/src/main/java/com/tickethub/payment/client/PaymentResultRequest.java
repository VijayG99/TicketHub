package com.tickethub.payment.client;

import com.tickethub.payment.enums.PaymentResult;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentResultRequest {
    private final UUID paymentId;
    private final PaymentResult result;
    private final String reason;
}
