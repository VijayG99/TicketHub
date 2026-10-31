package com.tickethub.payment.provider;

import com.tickethub.payment.enums.ProviderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProviderResult {
    private final ProviderStatus status;
    private final String providerReference;
    private final String failureReason;
}
