package com.tickethub.payment.service;

import com.tickethub.payment.client.BookingClient;
import com.tickethub.payment.client.BookingSnapshot;
import com.tickethub.payment.client.PaymentResultAck;
import com.tickethub.payment.client.PaymentResultRequest;
import com.tickethub.payment.common.error.ErrorCode;
import com.tickethub.payment.common.exception.BusinessException;
import com.tickethub.payment.common.exception.BusinessRuleException;
import com.tickethub.payment.common.exception.DependencyTimeoutException;
import com.tickethub.payment.common.exception.DependencyUnavailableException;
import com.tickethub.payment.common.exception.ForbiddenException;
import com.tickethub.payment.common.exception.InvalidStateTransitionException;
import com.tickethub.payment.common.exception.ResourceNotFoundException;
import com.tickethub.payment.common.security.CurrentUser;
import com.tickethub.payment.config.SimulatorProperties;
import com.tickethub.payment.dto.InitiatePaymentRequest;
import com.tickethub.payment.dto.PaymentInitiationResult;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.BookingStatus;
import com.tickethub.payment.enums.PaymentResult;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.mapper.PaymentMapper;
import com.tickethub.payment.provider.ProviderInvoker;
import com.tickethub.payment.provider.ProviderRequest;
import com.tickethub.payment.provider.ProviderResult;
import com.tickethub.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates payment initiation. Not transactional: state is written in short transactions
 * (PaymentStateService) and remote calls (Booking, provider) are made outside any transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentStateService stateService;
    private final PaymentRepository paymentRepository;
    private final BookingClient bookingClient;
    private final ProviderInvoker providerInvoker;
    private final SimulatorProperties simulatorProperties;

    public PaymentInitiationResult initiate(CurrentUser user, InitiatePaymentRequest request) {
        final String customerId = user.requireUserId();
        final UUID bookingId = request.getBookingId();

        if (request.getSimulatedOutcome() != null && !simulatorProperties.isAllowClientOutcome()) {
            throw new BusinessRuleException("simulatedOutcome is not allowed in this environment");
        }

        // ST-24: repeated initiation returns the existing live payment instead of creating another.
        Optional<Payment> existing = stateService.findLive(bookingId);
        if (existing.isPresent()) {
            return new PaymentInitiationResult(owned(existing.get(), customerId), false);
        }

        BookingSnapshot booking = bookingClient.getBooking(bookingId);
        if (!customerId.equals(booking.getCustomerId())) {
            throw new ForbiddenException("This booking belongs to another customer");
        }
        Instant now = Instant.now();
        if (booking.getStatus() == BookingStatus.EXPIRED
                || (booking.getStatus() == BookingStatus.PENDING_PAYMENT && !booking.getExpiresAt().isAfter(now))) {
            throw new BusinessException(ErrorCode.BOOKING_EXPIRED, HttpStatus.CONFLICT,
                    "The booking reservation has expired and can no longer be paid");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException(
                    "Booking is " + booking.getStatus() + " and cannot be paid");
        }

        Payment payment;
        try {
            payment = stateService.create(booking, customerId, providerInvoker.gateway().name());
        } catch (DataIntegrityViolationException e) {
            // concurrent initiation for the same booking: the unique index let exactly one through
            Payment winner = stateService.findLive(bookingId).orElseThrow(() -> e);
            return new PaymentInitiationResult(owned(winner, customerId), false);
        }

        ProviderResult result;
        try {
            result = providerInvoker.initiate(new ProviderRequest(
                    payment.getId(), payment.getAmount(), payment.getCurrency(), request.getSimulatedOutcome()));
        } catch (DependencyTimeoutException e) {
            // SC-05 / S9: the provider may or may not have charged the customer. We do NOT guess:
            // the payment becomes UNKNOWN and the reconciliation job asks the provider what happened.
            stateService.markUnknown(payment.getId(), "Provider timed out; outcome unknown");
            throw e;
        } catch (DependencyUnavailableException e) {
            // SC-16: provider not reached, nothing charged; booking stays payable until it expires.
            stateService.markCancelled(payment.getId(), "Provider unavailable");
            throw e;
        }

        // S4: applying the result also writes PaymentSucceeded/PaymentFailed to the outbox in the same
        // transaction. There is NO direct call to Booking any more; Booking reacts to the event.
        Payment updated = stateService.applyProviderResult(payment.getId(), result);
        return new PaymentInitiationResult(PaymentMapper.toResponse(updated), true);
    }

    public PaymentResponse get(UUID paymentId, CurrentUser user) {
        String userId = user.requireUserId();
        Optional<Payment> found = user.isAdmin()
                ? paymentRepository.findById(paymentId)
                : paymentRepository.findByIdAndCustomerId(paymentId, userId);
        return found.map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
    }

    private PaymentResponse owned(Payment p, String customerId) {
        if (!p.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("This booking belongs to another customer");
        }
        return PaymentMapper.toResponse(p);
    }
}
