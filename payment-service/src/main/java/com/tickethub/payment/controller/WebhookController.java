package com.tickethub.payment.controller;

import com.tickethub.payment.dto.WebhookAck;
import com.tickethub.payment.service.WebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment Webhook")
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /** The raw body is received as a String so the signature is verified over the exact bytes sent. */
    @PostMapping(consumes = "application/json")
    public WebhookAck receive(@RequestBody String rawBody,
                              @RequestHeader(value = "X-Signature", required = false) String signature) {
        return webhookService.process(rawBody, signature);
    }
}
