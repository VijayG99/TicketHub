package com.tickethub.payment.dto;

import com.tickethub.payment.enums.ProviderStatus;
import lombok.Data;

/** Provider callback body. eventId is the provider's unique id for this notification. */
@Data
public class WebhookPayload {
    private String eventId;
    private String providerReference;
    private ProviderStatus status;
    private String failureReason;
}
