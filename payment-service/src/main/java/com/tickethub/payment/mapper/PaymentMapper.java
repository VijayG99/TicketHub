package com.tickethub.payment.mapper;

import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.entity.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .bookingId(p.getBookingId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .provider(p.getProvider())
                .providerReference(p.getProviderReference())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
