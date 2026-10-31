package com.tickethub.payment.provider;

import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.enums.SimulatedOutcome;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProviderRequest {
    private final UUID paymentId;
    private final BigDecimal amount;
    private final CurrencyCode currency;
    /** Only honoured by the simulated provider, and only when enabled by configuration. */
    private final SimulatedOutcome simulatedOutcome;
}
