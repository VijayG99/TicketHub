package com.tickethub.payment.provider;

import com.tickethub.payment.enums.PaymentProviderName;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The seam behind which every payment provider lives. Booking never knows which provider is used,
 * so a real sandbox provider (ST-22) can replace the simulator without changing Booking.
 */
public interface PaymentProviderGateway {

    PaymentProviderName name();

    ProviderResult initiate(ProviderRequest request);

    /**
     * S9: "what really happened to this payment?" Used after a timeout (status UNKNOWN) to ask the
     * provider instead of guessing. Returns null when the provider has no record of it.
     */
    default ProviderResult lookup(UUID paymentId) {
        return null;
    }

    /**
     * S5: refund a captured payment. MUST be idempotent per payment id: calling it twice for the same
     * payment refunds once and returns the same refund reference.
     */
    default String refund(UUID paymentId, String providerReference, BigDecimal amount) {
        throw new com.tickethub.payment.common.exception.DependencyUnavailableException(
                "This payment provider does not support refunds");
    }
}
