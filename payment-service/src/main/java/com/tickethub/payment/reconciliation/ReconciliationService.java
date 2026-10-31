package com.tickethub.payment.reconciliation;

import com.tickethub.payment.client.BookingClient;
import com.tickethub.payment.client.BookingSnapshot;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.enums.ProviderStatus;
import com.tickethub.payment.provider.ProviderInvoker;
import com.tickethub.payment.provider.ProviderResult;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.service.PaymentRefundService;
import com.tickethub.payment.service.PaymentStateService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * S8 + S9: the safety net. Even a good event design drifts (a dead-lettered message, a lost offset,
 * a provider timeout). This job compares what each system believes and repairs or flags the difference.
 * The PROVIDER is the source of truth for money.
 *
 * <pre>
 *  Check A  payments stuck INITIATED / UNKNOWN / PENDING (e.g. after a crash)     -> ask the provider (lookup) -> SUCCESS or FAILED   (S9)
 *  Check B  payments SUCCESS but booking is...
 *             CONFIRMED          fine
 *             PENDING_PAYMENT    Booking never heard about the payment -> re-publish PaymentSucceeded
 *             EXPIRED / FAILED / CANCELLED   customer paid for nothing -> refund
 *  Check C  bookings CONFIRMED but no captured payment here -> FLAG (cannot be auto-fixed)
 * </pre>
 * Only items older than a grace period are examined, so the job never races the normal event flow.
 * It is safe to run any number of times: every repair is idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateService stateService;
    private final PaymentRefundService refundService;
    private final ProviderInvoker providerInvoker;
    private final BookingClient bookingClient;
    private final io.micrometer.core.instrument.MeterRegistry registry;

    @Value("${payment.reconciliation.grace-seconds:60}")
    private long graceSeconds;

    @Value("${payment.reconciliation.booking-lookback-minutes:60}")
    private int lookbackMinutes;

    private final AtomicReference<ReconciliationReport> last = new AtomicReference<>();

    public ReconciliationReport lastReport() {
        return last.get();
    }

    public synchronized ReconciliationReport run() {
        Instant cutoff = Instant.now().minus(graceSeconds, ChronoUnit.SECONDS);
        List<String> fixed = new ArrayList<>();
        List<String> flagged = new ArrayList<>();

        // ---- Check A: unresolved payments -> ask the provider
        List<Payment> unresolved = paymentRepository.findByStatusInAndUpdatedAtBefore(
                EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.UNKNOWN, PaymentStatus.PENDING), cutoff, PageRequest.of(0, 200));
        for (Payment p : unresolved) {
            try {
                ProviderResult result = providerInvoker.lookup(p.getId());
                if (result == null) {
                    flagged.add("payment " + p.getId() + " is " + p.getStatus() + " and the provider has no record of it");
                } else if (result.getStatus() == ProviderStatus.SUCCEEDED || result.getStatus() == ProviderStatus.FAILED) {
                    stateService.applyProviderResult(p.getId(), result);
                    fixed.add("payment " + p.getId() + " was " + p.getStatus() + ", provider says "
                            + result.getStatus() + " -> updated and event published");
                }
            } catch (RuntimeException e) {
                flagged.add("payment " + p.getId() + " lookup failed: " + e.getMessage());
            }
        }

        // ---- Check B: captured payments vs booking state
        List<Payment> captured = paymentRepository.findByStatusInAndUpdatedAtBefore(
                EnumSet.of(PaymentStatus.SUCCESS), cutoff, PageRequest.of(0, 200));
        for (Payment p : captured) {
            try {
                BookingSnapshot booking = bookingClient.getBooking(p.getBookingId());
                switch (booking.getStatus()) {
                    case CONFIRMED -> { /* consistent */ }
                    case PENDING_PAYMENT -> {
                        stateService.republishSucceeded(p.getId());
                        fixed.add("payment " + p.getId() + " captured but booking " + p.getBookingId()
                                + " still PENDING_PAYMENT -> PaymentSucceeded re-published");
                    }
                    default -> {
                        refundService.refund(paymentRepository.findById(p.getId()).orElse(p), null, "Reconciliation", 0);
                        fixed.add("payment " + p.getId() + " captured but booking " + p.getBookingId() + " is "
                                + booking.getStatus() + " -> refunded");
                    }
                }
            } catch (RuntimeException e) {
                flagged.add("payment " + p.getId() + " could not be checked: " + e.getMessage());
            }
        }

        // ---- Check C: confirmed bookings that have no captured payment
        int bookingsChecked = 0;
        try {
            List<UUID> confirmed = bookingClient.listConfirmedBookingIds(lookbackMinutes);
            bookingsChecked = confirmed.size();
            for (UUID bookingId : confirmed) {
                boolean paid = paymentRepository.findFirstByBookingIdAndStatusIn(
                        bookingId, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED)).isPresent();
                if (!paid) {
                    flagged.add("booking " + bookingId + " is CONFIRMED but Payment has no captured payment for it");
                }
            }
        } catch (RuntimeException e) {
            flagged.add("could not list confirmed bookings: " + e.getMessage());
        }

        ReconciliationReport report = new ReconciliationReport(Instant.now(),
                unresolved.size() + captured.size(), bookingsChecked, fixed, flagged);
        last.set(report);
        // ST-77: how much drift reconciliation found and repaired / could only flag
        registry.counter("tickethub_reconciliation_fixed_total").increment(fixed.size());
        registry.counter("tickethub_reconciliation_flagged_total").increment(flagged.size());
        log.info("Reconciliation finished: paymentsChecked={} bookingsChecked={} fixed={} flagged={}",
                report.paymentsChecked(), bookingsChecked, fixed.size(), flagged.size());
        fixed.forEach(f -> log.warn("RECONCILIATION FIXED: {}", f));
        flagged.forEach(f -> log.warn("RECONCILIATION FLAGGED: {}", f));
        return report;
    }
}
