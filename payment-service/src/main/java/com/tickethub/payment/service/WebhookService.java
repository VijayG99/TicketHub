package com.tickethub.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.common.exception.BusinessRuleException;
import com.tickethub.payment.common.exception.ResourceNotFoundException;
import com.tickethub.payment.dto.WebhookAck;
import com.tickethub.payment.dto.WebhookPayload;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.ProcessedWebhookEvent;
import com.tickethub.payment.enums.PaymentStatus;
import com.tickethub.payment.enums.ProviderStatus;
import com.tickethub.payment.enums.WebhookOutcome;
import com.tickethub.payment.exception.SignatureInvalidException;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.repository.ProcessedWebhookEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Provider webhook handling: verified, idempotent and safe against late or conflicting callbacks. */
@Slf4j
@Service
public class WebhookService {

    private final SignatureVerifier verifier;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedRepository;
    private final PaymentStateService stateService;
    private final PaymentService paymentService;
    private final TransactionTemplate tx;

    public WebhookService(SignatureVerifier verifier, ObjectMapper objectMapper, PaymentRepository paymentRepository,
                          ProcessedWebhookEventRepository processedRepository, PaymentStateService stateService,
                          PaymentService paymentService, PlatformTransactionManager txManager) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
        this.processedRepository = processedRepository;
        this.stateService = stateService;
        this.paymentService = paymentService;
        this.tx = new TransactionTemplate(txManager);
    }

    public WebhookAck process(String rawBody, String signatureHeader) {
        // 1. Verify the signature over the exact bytes received. Invalid callbacks are rejected and logged.
        if (!verifier.verify(rawBody, signatureHeader)) {
            log.warn("Rejected webhook with invalid or missing signature");
            throw new SignatureInvalidException();
        }
        WebhookPayload payload = parse(rawBody);

        // 2. Apply once. A repeated eventId (SC-10) or a late/conflicting callback (SC-11) changes nothing.
        Result result;
        try {
            result = tx.execute(status -> apply(payload));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate webhook eventId={} (concurrent delivery) ignored", payload.getEventId());
            return new WebhookAck(WebhookOutcome.DUPLICATE_IGNORED);
        }

        // 3. S4: nothing more to do. The state change and its PaymentSucceeded/PaymentFailed event were
        //    saved together in step 2 (outbox); the relay delivers the event to Booking.
        return new WebhookAck(result.outcome);
    }

    private Result apply(WebhookPayload payload) {
        if (processedRepository.existsById(payload.getEventId())) {
            log.info("Duplicate webhook eventId={} ignored", payload.getEventId());
            return new Result(WebhookOutcome.DUPLICATE_IGNORED, null, false);
        }
        Payment payment = paymentRepository.findByProviderReference(payload.getProviderReference())
                .orElseThrow(() -> new ResourceNotFoundException("No payment for the given providerReference"));

        ProcessedWebhookEvent processed = new ProcessedWebhookEvent();
        processed.setEventId(payload.getEventId());
        processed.setPaymentId(payment.getId());
        processed.setProcessedAt(Instant.now());
        processedRepository.saveAndFlush(processed);

        if (payment.getStatus().isFinal()) {
            log.warn("Webhook eventId={} for payment {} already {}; callback status {} ignored",
                    payload.getEventId(), payment.getId(), payment.getStatus(), payload.getStatus());
            return new Result(WebhookOutcome.IGNORED_ALREADY_FINAL, payment.getId(), false);
        }
        if (payload.getStatus() == ProviderStatus.PENDING) {
            return new Result(WebhookOutcome.IGNORED_NO_CHANGE, payment.getId(), false);
        }
        stateService.transition(payment,
                payload.getStatus() == ProviderStatus.SUCCEEDED ? PaymentStatus.SUCCESS : PaymentStatus.FAILED,
                payload.getFailureReason());
        paymentRepository.save(payment);
        return new Result(WebhookOutcome.APPLIED, payment.getId(), true);
    }

    private WebhookPayload parse(String rawBody) {
        try {
            WebhookPayload p = objectMapper.readValue(rawBody, WebhookPayload.class);
            if (p.getEventId() == null || p.getEventId().isBlank() || p.getEventId().length() > 100
                    || p.getProviderReference() == null || p.getProviderReference().isBlank()
                    || p.getStatus() == null) {
                throw new BusinessRuleException("Webhook payload must contain eventId, providerReference and status");
            }
            return p;
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Webhook payload is not valid JSON");
        }
    }

    private static class Result {
        private final WebhookOutcome outcome;
        private final UUID paymentId;
        private final boolean notifyBooking;

        private Result(WebhookOutcome outcome, UUID paymentId, boolean notifyBooking) {
            this.outcome = outcome;
            this.paymentId = paymentId;
            this.notifyBooking = notifyBooking;
        }
    }
}
