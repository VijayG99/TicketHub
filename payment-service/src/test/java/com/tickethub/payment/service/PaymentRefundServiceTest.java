package com.tickethub.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.provider.ProviderInvoker;
import com.tickethub.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S5 / S6: a payment is refunded once, and a stale request is ignored. */
class PaymentRefundServiceTest {

    private PaymentRepository repository;
    private PaymentStateService stateService;
    private ProviderInvoker invoker;
    private PaymentRefundService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        stateService = mock(PaymentStateService.class);
        invoker = mock(ProviderInvoker.class);
        service = new PaymentRefundService(repository, stateService, invoker);
    }

    private Payment payment(PaymentStatus status, long lastVersion) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(UUID.randomUUID());
        p.setStatus(status);
        p.setAmount(new BigDecimal("100.00"));
        p.setProviderReference("REF-1");
        p.setLastBookingEventVersion(lastVersion);
        return p;
    }

    @Test
    void capturedPaymentIsRefundedAndRecorded() {
        Payment p = payment(PaymentStatus.SUCCESS, 0);
        UUID eventId = UUID.randomUUID();
        when(invoker.refund(p.getId(), "REF-1", p.getAmount())).thenReturn("RF-9");
        service.refund(p, eventId, "BookingRejected", 1);
        verify(stateService).completeRefund(p.getId(), "RF-9", 1, eventId, "BookingRejected");
    }

    @Test
    void alreadyRefundedPaymentIsNeverRefundedTwice() {
        service.refund(payment(PaymentStatus.REFUNDED, 0), UUID.randomUUID(), "BookingCancelled", 2);
        verify(invoker, never()).refund(any(), any(), any());
        verify(stateService, never()).completeRefund(any(), any(), anyLong(), any(), any());
    }

    @Test
    void staleRequestIsIgnored() {
        Payment p = payment(PaymentStatus.SUCCESS, 2);
        service.refund(p, UUID.randomUUID(), "BookingRejected", 1);
        verify(invoker, never()).refund(any(), any(), any());
    }
}
