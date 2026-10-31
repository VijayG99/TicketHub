package com.tickethub.payment.consumer;

import com.tickethub.payment.event.BookingEvent;
import com.tickethub.payment.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S5: reacts to Booking's compensation requests.
 *   BookingRejected  (late payment: booking already expired / cancelled)  -> refund
 *   BookingCancelled (customer cancelled a CONFIRMED booking)             -> refund
 * Any other type is ignored on purpose, so newer producers do not break us (S13).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventHandler {

    private final PaymentRefundService refundService;

    public void handle(BookingEvent e) {
        if (e.getEventId() == null || e.getBookingId() == null || e.getEventType() == null) {
            throw new IllegalArgumentException("Booking event is missing eventId, eventType or bookingId"); // -> DLT
        }
        long version = e.getAggregateVersion() == null ? 0 : e.getAggregateVersion();
        switch (e.getEventType()) {
            case "BookingRejected", "BookingCancelled" ->
                    refundService.refundForBooking(e.getBookingId(), e.getEventId(), e.getEventType(), version);
            default -> log.warn("Ignoring unknown booking event type {}", e.getEventType());
        }
    }
}
