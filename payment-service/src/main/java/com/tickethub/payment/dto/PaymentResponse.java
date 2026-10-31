package com.tickethub.payment.dto;

import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.enums.PaymentProviderName;
import com.tickethub.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {
    private final UUID paymentId;
    private final UUID bookingId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    private final PaymentStatus status;
    private final PaymentProviderName provider;
    private final String providerReference;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;
}
