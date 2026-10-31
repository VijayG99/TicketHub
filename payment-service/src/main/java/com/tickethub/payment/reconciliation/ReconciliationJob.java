package com.tickethub.payment.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** S8: runs the reconciliation periodically. Interval and on/off switch are configuration. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final ReconciliationService service;

    @Value("${payment.reconciliation.enabled:true}")
    private boolean enabled;

    @Scheduled(initialDelayString = "${payment.reconciliation.interval-ms:60000}",
            fixedDelayString = "${payment.reconciliation.interval-ms:60000}")
    public void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            service.run();
        } catch (Exception e) {
            log.error("Reconciliation run failed: {}", e.toString());
        }
    }
}
