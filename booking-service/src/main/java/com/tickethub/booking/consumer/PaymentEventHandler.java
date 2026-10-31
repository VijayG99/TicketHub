package com.tickethub.booking.consumer;

import com.tickethub.booking.dto.PaymentResultRequest;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.enums.PaymentResult;
import com.tickethub.booking.event.PaymentEvent;
import com.tickethub.booking.outbox.InboxService;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4/S5/S6: applies ONE Payment event to Booking, all inside ONE database transaction:
 *
 * <pre>
 *   1. inbox insert   (duplicate delivery?  -> stop, nothing else happens)         [S2/S4]
 *   2. version check  (older than what we already applied? -> ignore)               [S6]
 *   3. business step  PaymentSucceeded -> confirm (or BookingRejected if too late)  [S4/S5]
 *                     PaymentFailed    -> PAYMENT_FAILED, seats released            [S4]
 *                     PaymentRefunded  -> saga COMPENSATED                          [S5]
 *   4. outbox insert  (done inside BookingService, same transaction)               [S3]
 * </pre>
 * If anything throws, the whole transaction rolls back (inbox row too), Kafka redelivers, and we try again.
 * So the event is never "half applied".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final InboxService inbox;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Transactional
    public void handle(PaymentEvent e) {
        if (e.getEventId() == null || e.getBookingId() == null || e.getEventType() == null) {
            // bad data: retrying cannot fix it, so IllegalArgumentException goes straight to the dead-letter topic
            throw new IllegalArgumentException("Payment event is missing eventId, eventType or bookingId");
        }
        if (!inbox.firstTime(e.getEventId(), e.getEventType())) {
            log.info("Duplicate {} eventId={} bookingId={} ignored", e.getEventType(), e.getEventId(), e.getBookingId());
            return;
        }
        Booking booking = bookingRepository.findById(e.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown booking " + e.getBookingId()));

        long version = e.getAggregateVersion() == null ? 0 : e.getAggregateVersion();
        if (version > 0 && version <= booking.getLastPaymentEventVersion()) {
            log.warn("STALE {} ignored bookingId={} eventVersion={} alreadyApplied={}",
                    e.getEventType(), e.getBookingId(), version, booking.getLastPaymentEventVersion());
            return;
        }

        switch (e.getEventType()) {
            case "PaymentSucceeded" -> bookingService.applyPaymentResult(e.getBookingId(), request(e, PaymentResult.SUCCESS));
            case "PaymentFailed" -> bookingService.applyPaymentResult(e.getBookingId(), request(e, PaymentResult.FAILED));
            case "PaymentRefunded" -> bookingService.recordRefund(e.getBookingId(), e.getPaymentId());
            // unknown types from a newer producer are ignored on purpose (S13: consumers tolerate the unknown)
            default -> log.warn("Ignoring unknown payment event type {}", e.getEventType());
        }
        if (version > booking.getLastPaymentEventVersion()) {
            booking.setLastPaymentEventVersion(version);
            bookingRepository.save(booking);
        }
    }

    private static PaymentResultRequest request(PaymentEvent e, PaymentResult result) {
        PaymentResultRequest r = new PaymentResultRequest();
        r.setPaymentId(e.getPaymentId());
        r.setResult(result);
        r.setReason(e.getReason());
        return r;
    }
}
