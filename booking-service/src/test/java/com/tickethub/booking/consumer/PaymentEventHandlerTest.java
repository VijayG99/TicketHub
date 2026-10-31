package com.tickethub.booking.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.enums.PaymentResult;
import com.tickethub.booking.event.PaymentEvent;
import com.tickethub.booking.outbox.InboxService;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S2 / S4 / S6: how Booking reacts to Payment events (database and BookingService mocked). */
class PaymentEventHandlerTest {

    private InboxService inbox;
    private BookingRepository repository;
    private BookingService bookingService;
    private PaymentEventHandler handler;
    private Booking booking;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        inbox = mock(InboxService.class);
        repository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        handler = new PaymentEventHandler(inbox, repository, bookingService);
        bookingId = UUID.randomUUID();
        booking = new Booking();
        booking.setId(bookingId);
        when(repository.findById(bookingId)).thenReturn(Optional.of(booking));
    }

    private PaymentEvent event(String type, long version) {
        PaymentEvent e = new PaymentEvent();
        e.setEventId(UUID.randomUUID());
        e.setEventType(type);
        e.setAggregateVersion(version);
        e.setBookingId(bookingId);
        e.setPaymentId(UUID.randomUUID());
        return e;
    }

    @Test
    void paymentSucceededIsAppliedOnce() {
        when(inbox.firstTime(any(), any())).thenReturn(true);
        handler.handle(event("PaymentSucceeded", 1));
        ArgumentCaptor<com.tickethub.booking.dto.PaymentResultRequest> req =
                ArgumentCaptor.forClass(com.tickethub.booking.dto.PaymentResultRequest.class);
        verify(bookingService).applyPaymentResult(org.mockito.ArgumentMatchers.eq(bookingId), req.capture());
        org.junit.jupiter.api.Assertions.assertEquals(PaymentResult.SUCCESS, req.getValue().getResult());
        org.junit.jupiter.api.Assertions.assertEquals(1, booking.getLastPaymentEventVersion());
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        when(inbox.firstTime(any(), any())).thenReturn(false); // inbox says: seen before
        handler.handle(event("PaymentSucceeded", 1));
        verify(bookingService, never()).applyPaymentResult(any(), any());
    }

    @Test
    void staleEventIsIgnored() {
        when(inbox.firstTime(any(), any())).thenReturn(true);
        booking.setLastPaymentEventVersion(2); // refund (v2) already applied
        handler.handle(event("PaymentSucceeded", 1)); // older event arrives late
        verify(bookingService, never()).applyPaymentResult(any(), any());
    }

    @Test
    void refundCompletesTheSaga() {
        when(inbox.firstTime(any(), any())).thenReturn(true);
        PaymentEvent e = event("PaymentRefunded", 2);
        handler.handle(e);
        verify(bookingService).recordRefund(bookingId, e.getPaymentId());
    }

    @Test
    void badDataGoesToTheDeadLetterTopic() {
        PaymentEvent e = new PaymentEvent(); // no ids at all
        assertThrows(IllegalArgumentException.class, () -> handler.handle(e));
    }
}
