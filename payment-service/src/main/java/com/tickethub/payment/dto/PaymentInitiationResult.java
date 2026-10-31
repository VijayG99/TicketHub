package com.tickethub.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** created = false when an existing payment for the booking was returned (ST-24). */
@Getter
@AllArgsConstructor
public class PaymentInitiationResult {
    private final PaymentResponse payment;
    private final boolean created;
}
