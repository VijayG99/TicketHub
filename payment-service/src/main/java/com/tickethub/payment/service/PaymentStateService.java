package com.tickethub.payment.service;

import com.tickethub.payment.client.BookingSnapshot;
import com.tickethub.payment.common.exception.ResourceNotFoundException;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.PaymentProviderName;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.provider.ProviderResult;
import com.tickethub.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.tickethub.payment.event.PaymentSagaEvent;
import com.tickethub.payment.outbox.InboxService;
import com.tickethub.payment.outbox.OutboxService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short local transactions for payment state. Remote calls are never made inside these. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateService {

    static final Set<PaymentStatus> LIVE = EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PENDING,
            PaymentStatus.UNKNOWN, PaymentStatus.SUCCESS);

    private final PaymentRepository paymentRepository;
    private final OutboxService outbox;
    private final InboxService inbox;
    private final io.micrometer.core.instrument.MeterRegistry registry;

    /** Topic carrying PaymentSucceeded / PaymentFailed / PaymentRefunded to Booking. */
    @Value("${tickethub.kafka.payment-events-topic:payment-events}")
    private String paymentEventsTopic;

    @Transactional(readOnly = true)
    public Optional<Payment> findLive(UUID bookingId) {
        return paymentRepository.findFirstByBookingIdAndStatusIn(bookingId, LIVE);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    }

    @Transactional
    public Payment create(BookingSnapshot booking, String customerId, PaymentProviderName provider) {
        Instant now = Instant.now();
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(booking.getBookingId());
        p.setCustomerId(customerId);
        p.setAmount(booking.getAmount()); // snapshot from the booking, recorded at initiation (ST-46)
        p.setCurrency(booking.getCurrency());
        p.setStatus(PaymentStatus.INITIATED);
        p.setProvider(provider);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        Payment saved = paymentRepository.saveAndFlush(p);
        log.info("Payment INITIATED paymentId={} bookingId={} amount={} {}", saved.getId(),
                saved.getBookingId(), saved.getAmount(), saved.getCurrency());
        return saved;
    }

    @Transactional
    public Payment applyProviderResult(UUID paymentId, ProviderResult result) {
        Payment p = get(paymentId);
        p.setProviderReference(result.getProviderReference());
        switch (result.getStatus()) {
            case SUCCEEDED:
                transition(p, PaymentStatus.SUCCESS, null);
                break;
            case FAILED:
                transition(p, PaymentStatus.FAILED, result.getFailureReason());
                break;
            default:
                transition(p, PaymentStatus.PENDING, null);
        }
        return paymentRepository.save(p);
    }

    /** Provider timed out: the real outcome is unknown, so the payment stays PENDING (never guessed). */
    @Transactional
    public void markPending(UUID paymentId, String reason) {
        Payment p = get(paymentId);
        transition(p, PaymentStatus.PENDING, reason);
        paymentRepository.save(p);
    }

    /**
     * S9: the provider call timed out. The customer MAY have been charged, so we neither succeed nor fail
     * the payment: it becomes UNKNOWN and the reconciliation job asks the provider for the truth.
     */
    @Transactional
    public void markUnknown(UUID paymentId, String reason) {
        Payment p = get(paymentId);
        transition(p, PaymentStatus.UNKNOWN, reason);
        paymentRepository.save(p);
    }

    /**
     * S5: the provider refunded the money. ONE transaction does all of:
     * inbox check (duplicate trigger event?) -> status REFUNDED -> PaymentRefunded into the outbox.
     *
     * @param triggerEventId id of the BookingRejected/BookingCancelled event that caused the refund,
     *                       or null when reconciliation refunds on its own
     */
    @Transactional
    public void completeRefund(UUID paymentId, String refundReference, long bookingEventVersion,
                               UUID triggerEventId, String triggerEventType) {
        if (triggerEventId != null && !inbox.firstTime(triggerEventId, triggerEventType)) {
            log.info("Duplicate {} eventId={} ignored", triggerEventType, triggerEventId);
            return;
        }
        Payment p = get(paymentId);
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            return; // already refunded: a payment is never refunded twice
        }
        transition(p, PaymentStatus.REFUNDED, null);
        if (p.getStatus() != PaymentStatus.REFUNDED) {
            return; // illegal move (e.g. payment was not SUCCESS); transition() already logged it
        }
        p.setRefundReference(refundReference);
        if (bookingEventVersion > p.getLastBookingEventVersion()) {
            p.setLastBookingEventVersion(bookingEventVersion);
        }
        paymentRepository.save(p);
        emit(p, "PaymentRefunded", 2, "Refunded " + refundReference);
        registry.counter("tickethub_refunds_total").increment(); // ST-77
    }

    /**
     * S8: re-announce a SUCCESS payment whose PaymentSucceeded event never made Booking confirm.
     * Uses a NEW event id (the old one is already in the outbox/inbox), Booking applies it idempotently.
     */
    @Transactional
    public void republishSucceeded(UUID paymentId) {
        Payment p = get(paymentId);
        if (p.getStatus() != PaymentStatus.SUCCESS) {
            return;
        }
        PaymentSagaEvent event = PaymentSagaEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PaymentSucceeded").eventVersion(1).aggregateVersion(1)
                .occurredAt(Instant.now())
                .bookingId(p.getBookingId()).paymentId(p.getId())
                .amount(p.getAmount()).currency(p.getCurrency())
                .reason("Re-published by reconciliation")
                .build();
        outbox.append(event.getEventId(), p.getBookingId(), event.getEventType(), paymentEventsTopic, event);
        log.warn("Reconciliation re-published PaymentSucceeded for payment {} booking {}", p.getId(), p.getBookingId());
    }

    /** The provider was never reached (circuit open / unreachable): nothing was charged. */
    @Transactional
    public void markCancelled(UUID paymentId, String reason) {
        Payment p = get(paymentId);
        transition(p, PaymentStatus.CANCELLED, reason);
        paymentRepository.save(p);
    }

    @Transactional
    public void markBookingNotified(UUID paymentId) {
        Payment p = get(paymentId);
        p.setBookingNotified(true);
        p.setSyncAttempts(p.getSyncAttempts() + 1);
        p.setUpdatedAt(Instant.now());
        paymentRepository.save(p);
    }

    @Transactional
    public void recordFailedSync(UUID paymentId) {
        Payment p = get(paymentId);
        p.setSyncAttempts(p.getSyncAttempts() + 1);
        p.setUpdatedAt(Instant.now());
        paymentRepository.save(p);
    }

    /** Applies the state change; returns false when the payment is already final or the move is illegal. */
    void transition(Payment p, PaymentStatus target, String reason) {
        if (p.getStatus() == target) {
            return;
        }
        if (!p.getStatus().canTransitionTo(target)) {
            log.warn("Ignoring illegal payment transition {} -> {} for payment {}", p.getStatus(), target, p.getId());
            return;
        }
        p.setStatus(target);
        if (reason != null) {
            p.setFailureReason(reason);
        }
        p.setUpdatedAt(Instant.now());
        log.info("Payment {} -> {}", p.getId(), target);
        // S4: a final payment result is announced by EVENT (via the outbox, same transaction as this
        // state change). Payment no longer calls Booking over REST.
        if (target == PaymentStatus.SUCCESS) {
            emit(p, "PaymentSucceeded", 1, null);
        } else if (target == PaymentStatus.FAILED) {
            emit(p, "PaymentFailed", 1, reason);
        }
    }

    /** Appends one saga event to the outbox. Deterministic id per (type, payment): appending twice is a no-op. */
    private void emit(Payment p, String type, long aggregateVersion, String reason) {
        PaymentSagaEvent event = PaymentSagaEvent.builder()
                .eventId(UUID.nameUUIDFromBytes((type + ":" + p.getId()).getBytes(StandardCharsets.UTF_8)))
                .eventType(type)
                .eventVersion(1)
                .aggregateVersion(aggregateVersion)
                .occurredAt(Instant.now())
                .bookingId(p.getBookingId())
                .paymentId(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .reason(reason)
                .build();
        outbox.append(event.getEventId(), p.getBookingId(), type, paymentEventsTopic, event);
    }
}
