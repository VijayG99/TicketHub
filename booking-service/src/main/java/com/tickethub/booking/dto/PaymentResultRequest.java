package com.tickethub.booking.dto;

import com.tickethub.booking.enums.PaymentResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class PaymentResultRequest {
    @NotNull(message = "paymentId is required")
    private UUID paymentId;

    @NotNull(message = "result is required")
    private PaymentResult result;

    @Size(max = 300, message = "reason must be at most 300 characters")
    private String reason;
}
