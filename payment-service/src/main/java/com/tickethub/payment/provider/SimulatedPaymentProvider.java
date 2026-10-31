package com.tickethub.payment.provider;

import com.tickethub.payment.common.exception.DependencyUnavailableException;
import com.tickethub.payment.config.SimulatorProperties;
import com.tickethub.payment.enums.PaymentProviderName;
import com.tickethub.payment.enums.ProviderStatus;
import com.tickethub.payment.enums.SimulatedOutcome;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Test/demo provider with a controllable outcome. No real card or payment data is ever used. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProviderGateway {

    private final SimulatorProperties properties;

    /**
     * What this fake provider "remembers". A real provider keeps this on its servers; it is what lets
     * lookup() answer "did the customer really pay?" after our call timed out (S9).
     */
    private final java.util.Map<UUID, ProviderResult> charges = new java.util.concurrent.ConcurrentHashMap<>();
    /** One refund per payment, however many times refund() is called (S5 idempotency). */
    private final java.util.Map<UUID, String> refunds = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public PaymentProviderName name() {
        return PaymentProviderName.SIMULATED;
    }

    @Override
    public ProviderResult initiate(ProviderRequest request) {
        SimulatedOutcome outcome = resolveOutcome(request);
        log.info("Simulated provider: paymentId={} outcome={} latencyMs={}",
                request.getPaymentId(), outcome, properties.getLatencyMs());
        sleep(properties.getLatencyMs());
        String reference = "SIM-" + UUID.randomUUID();
        switch (outcome) {
            case FAILURE:
                return remember(request, new ProviderResult(ProviderStatus.FAILED, reference, "CARD_DECLINED_SIMULATED"));
            case PENDING_WEBHOOK:
                return remember(request, new ProviderResult(ProviderStatus.PENDING, reference, null));
            case TIMEOUT:
                // The money IS taken, but the answer comes too late. Remember the truth first, then hang,
                // so lookup() can later reveal it even though our caller gave up waiting.
                remember(request, new ProviderResult(ProviderStatus.SUCCEEDED, reference, null));
                sleep(properties.getHangMs());
                return charges.get(request.getPaymentId());
            default:
                return remember(request, new ProviderResult(ProviderStatus.SUCCEEDED, reference, null));
        }
    }

    private ProviderResult remember(ProviderRequest request, ProviderResult result) {
        charges.put(request.getPaymentId(), result);
        return result;
    }

    @Override
    public ProviderResult lookup(UUID paymentId) {
        return charges.get(paymentId);
    }

    @Override
    public String refund(UUID paymentId, String providerReference, java.math.BigDecimal amount) {
        return refunds.computeIfAbsent(paymentId, id -> {
            log.info("Simulated provider: refunded payment {} amount {}", id, amount);
            return "REF-" + UUID.randomUUID();
        });
    }

    private SimulatedOutcome resolveOutcome(ProviderRequest request) {
        if (request.getSimulatedOutcome() != null && properties.isAllowClientOutcome()) {
            return request.getSimulatedOutcome();
        }
        return properties.getDefaultOutcome();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Provider call was interrupted");
        }
    }
}
