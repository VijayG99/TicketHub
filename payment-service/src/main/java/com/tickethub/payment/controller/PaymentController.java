package com.tickethub.payment.controller;

import com.tickethub.payment.common.security.CurrentUser;
import com.tickethub.payment.dto.InitiatePaymentRequest;
import com.tickethub.payment.dto.PaymentInitiationResult;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 201 for a new payment, 200 when the booking already has a live payment (ST-24). */
    @PostMapping
    public ResponseEntity<PaymentResponse> initiate(CurrentUser user, @Valid @RequestBody InitiatePaymentRequest request) {
        PaymentInitiationResult result = paymentService.initiate(user, request);
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.getPayment());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, CurrentUser user) {
        return paymentService.get(id, user);
    }
}
