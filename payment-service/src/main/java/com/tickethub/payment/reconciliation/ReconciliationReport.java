package com.tickethub.payment.reconciliation;

import java.time.Instant;
import java.util.List;

/**
 * Result of one reconciliation run (S8).
 * fixed   = differences the job repaired itself
 * flagged = differences that need a human (or that the job cannot repair)
 */
public record ReconciliationReport(Instant ranAt, int paymentsChecked, int bookingsChecked,
                                   List<String> fixed, List<String> flagged) {
}
