package com.tickethub.payment.dto;

import com.tickethub.payment.enums.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/** No amount field: the amount always comes from the booking snapshot (ST-46, ST-54). */
@Data
public class InitiatePaymentRequest {
    @NotNull(message = "bookingId is required")
    private UUID bookingId;

    /** Test/demo only. Rejected unless the simulator allows client-chosen outcomes. */
    private SimulatedOutcome simulatedOutcome;
}
