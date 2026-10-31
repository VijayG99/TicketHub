package com.tickethub.payment.dto;

import com.tickethub.payment.enums.WebhookOutcome;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WebhookAck {
    private final WebhookOutcome outcome;
}
