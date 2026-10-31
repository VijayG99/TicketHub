package com.tickethub.payment.service;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.provider.ProviderInvoker;
import com.tickethub.payment.repository.PaymentRepository;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * S5: the COMPENSATION step of the saga. "Undo a payment" = refund it.
 *
 * <pre>
 *   find the captured payment of the booking
 *      already REFUNDED?      -> nothing to do               (idempotent)
 *      event older than one we already applied? -> ignore    (S6 ordering)
 *   ask the provider to refund   (provider call is idempotent per payment id, so repeating it is safe)
 *   ONE transaction: inbox row + status REFUNDED + PaymentRefunded event in the outbox
 * </pre>
 * Not @Transactional on purpose: the slow provider call must not hold a database transaction open.
 * "Never refunded twice" is guaranteed by three layers: payment status, inbox, provider idempotency key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateService stateService;
    private final ProviderInvoker providerInvoker;

    /** Triggered by BookingRejected / BookingCancelled. */
    public void refundForBooking(UUID bookingId, UUID eventId, String eventType, long eventVersion) {
        Payment p = paymentRepository
                .findFirstByBookingIdAndStatusIn(bookingId, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED))
                .orElseThrow(() -> new IllegalStateException(
                        "No captured payment found for booking " + bookingId + " (will be retried)"));
        refund(p, eventId, eventType, eventVersion);
    }

    /** Core refund. {@code eventId} is null when reconciliation refunds without a Booking event. */
    public void refund(Payment p, UUID eventId, String eventType, long eventVersion) {
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment {} already refunded; nothing to do", p.getId());
            return;
        }
        if (eventVersion > 0 && eventVersion <= p.getLastBookingEventVersion()) {
            log.warn("STALE {} ignored for payment {} (version {} <= applied {})",
                    eventType, p.getId(), eventVersion, p.getLastBookingEventVersion());
            return;
        }
        String refundReference = providerInvoker.refund(p.getId(), p.getProviderReference(), p.getAmount());
        stateService.completeRefund(p.getId(), refundReference, eventVersion, eventId, eventType);
        log.info("Payment {} refunded (booking {}, refund {})", p.getId(), p.getBookingId(), refundReference);
    }
}
