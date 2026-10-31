package com.tickethub.booking.service;

import com.tickethub.booking.client.ShowClient;
import com.tickethub.booking.client.ShowInfo;
import com.tickethub.booking.common.error.ErrorCode;
import com.tickethub.booking.common.exception.BusinessException;
import com.tickethub.booking.common.exception.BusinessRuleException;
import com.tickethub.booking.common.exception.InvalidStateTransitionException;
import com.tickethub.booking.common.exception.ResourceNotFoundException;
import com.tickethub.booking.common.security.CurrentUser;
import com.tickethub.booking.common.web.PageResponse;
import com.tickethub.booking.config.BookingProperties;
import com.tickethub.booking.dto.BookingCreationResult;
import com.tickethub.booking.dto.BookingInternalResponse;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingRequest;
import com.tickethub.booking.dto.PaymentResultAck;
import com.tickethub.booking.dto.PaymentResultRequest;
import com.tickethub.booking.dto.SeatReservationDto;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.enums.EventStatus;
import com.tickethub.booking.enums.PaymentResult;
import com.tickethub.booking.enums.PaymentResultOutcome;
import com.tickethub.booking.enums.SeatReservationState;
import com.tickethub.booking.enums.ShowStatus;
import com.tickethub.booking.enums.SagaState;
import com.tickethub.booking.event.BookingConfirmedEvent;
import com.tickethub.booking.event.BookingSagaEvent;
import com.tickethub.booking.outbox.OutboxService;
import com.tickethub.booking.saga.SagaLog;
import com.tickethub.booking.dto.SagaView;
import com.tickethub.booking.exception.SeatUnavailableException;
import com.tickethub.booking.mapper.BookingMapper;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.repository.BookingSeatRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository seatRepository;
    private final ShowClient showClient;
    private final BookingProperties properties;
    private final OutboxService outbox;
    private final SagaLog sagaLog;
    private final io.micrometer.core.instrument.MeterRegistry registry;
    private final String confirmedTopic;
    private final String bookingEventsTopic;
    private final TransactionTemplate tx;

    public BookingService(BookingRepository bookingRepository, BookingSeatRepository seatRepository,
                          ShowClient showClient, BookingProperties properties,
                          OutboxService outbox, SagaLog sagaLog, io.micrometer.core.instrument.MeterRegistry registry,
                          @Value("${tickethub.kafka.booking-confirmed-topic}") String confirmedTopic,
                          @Value("${tickethub.kafka.booking-events-topic:booking-events}") String bookingEventsTopic,
                          PlatformTransactionManager txManager) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.showClient = showClient;
        this.properties = properties;
        this.outbox = outbox;
        this.sagaLog = sagaLog;
        this.registry = registry;
        this.confirmedTopic = confirmedTopic;
        this.bookingEventsTopic = bookingEventsTopic;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates a PENDING_PAYMENT booking. Not transactional itself: the remote show lookup happens
     * outside any database transaction, then the reservation is written in one short transaction.
     */
    public BookingCreationResult create(CurrentUser user, CreateBookingRequest request, String rawIdempotencyKey) {
        final String customerId = user.requireUserId();
        final List<Long> seatIds = request.getSeatIds();
        final Set<Long> distinct = new HashSet<>(seatIds);
        if (distinct.size() != seatIds.size()) {
            throw new BusinessRuleException("seatIds must not contain duplicates");
        }
        if (seatIds.size() > properties.getMaxSeatsPerBooking()) {
            throw new BusinessRuleException("A booking can contain at most " + properties.getMaxSeatsPerBooking() + " seats");
        }
        final String key = normalizeKey(rawIdempotencyKey);

        // SC-13: a retry with the same key returns the original booking.
        if (key != null) {
            Optional<BookingResponse> replay = findReplay(customerId, key, request.getShowId(), distinct);
            if (replay.isPresent()) {
                log.info("Idempotent replay for customer={} key={} bookingId={}", customerId, key, replay.get().getBookingId());
                return new BookingCreationResult(replay.get(), true);
            }
        }

        ShowInfo show = showClient.getShow(request.getShowId());
        Map<Long, ShowInfo.SeatInfo> selected = validateShowAndSeats(show, seatIds);
        BigDecimal amount = show.getTicketPrice()
                .multiply(BigDecimal.valueOf(seatIds.size()))
                .setScale(2, RoundingMode.HALF_UP);

        try {
            BookingResponse created = tx.execute(status ->
                    createPending(customerId, user.getEmail(), key, show, selected, amount));
            return new BookingCreationResult(created, false);
        } catch (DataIntegrityViolationException e) {
            if (key != null && hasConstraint(e, "uq_booking_idempotency")) {
                Optional<BookingResponse> replay = findReplay(customerId, key, request.getShowId(), distinct);
                if (replay.isPresent()) {
                    return new BookingCreationResult(replay.get(), true);
                }
            }
            if (hasConstraint(e, "uq_active_show_seat")) {
                // SC-03: the database decided the race; this request lost.
                log.warn("Seat race lost for show={} seats={} customer={}", request.getShowId(), seatIds, customerId);
                throw new SeatUnavailableException(seatIds);
            }
            throw e;
        }
    }

    private BookingResponse createPending(String customerId, String email, String key, ShowInfo show,
                                          Map<Long, ShowInfo.SeatInfo> selected, BigDecimal amount) {
        expireStaleReservations(show.getShowId());

        List<BookingSeat> held = seatRepository.findByShowIdAndSeatIdInAndStateNot(
                show.getShowId(), selected.keySet(), SeatReservationState.AVAILABLE);
        if (!held.isEmpty()) {
            // SC-02: already BOOKED or RESERVED by another active booking.
            throw new SeatUnavailableException(held.stream().map(BookingSeat::getSeatId).toList());
        }

        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomerId(customerId);
        booking.setCustomerEmail(email);
        booking.setShowId(show.getShowId());
        booking.setEventTitle(show.getEventTitle());
        booking.setVenueName(show.getVenueName());
        booking.setShowStartTime(show.getStartTime());
        booking.setAmount(amount);
        booking.setCurrency(show.getCurrency());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setIdempotencyKey(key);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        booking.setExpiresAt(now.plus(properties.getReservationExpiryMinutes(), ChronoUnit.MINUTES));

        for (ShowInfo.SeatInfo info : selected.values()) {
            BookingSeat seat = new BookingSeat();
            seat.setBooking(booking);
            seat.setShowId(show.getShowId());
            seat.setSeatId(info.getSeatId());
            seat.setRowLabel(info.getRowLabel());
            seat.setSeatNumber(info.getSeatNumber());
            seat.setCategory(info.getCategory());
            seat.setState(SeatReservationState.RESERVED);
            seat.setReservedAt(now);
            booking.getSeats().add(seat);
        }
        // Flush now so the unique index rejects a conflicting seat inside this transaction.
        Booking saved = bookingRepository.saveAndFlush(booking);
        log.info("Booking created bookingId={} customer={} show={} seats={} amount={} expiresAt={}",
                saved.getId(), customerId, show.getShowId(), selected.keySet(), amount, saved.getExpiresAt());
        sagaLog.record(saved.getId(), SagaState.PAYMENT_PENDING, "BookingCreated",
                "Seats reserved until " + saved.getExpiresAt() + "; waiting for payment");
        return BookingMapper.toResponse(saved);
    }

    private Map<Long, ShowInfo.SeatInfo> validateShowAndSeats(ShowInfo show, List<Long> seatIds) {
        if (show.getEventStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleException("The event for this show is not published");
        }
        if (show.getShowStatus() != ShowStatus.OPEN) {
            throw new BusinessRuleException("The show is not open for booking (status " + show.getShowStatus() + ")");
        }
        if (!show.getStartTime().isAfter(Instant.now())) {
            throw new BusinessRuleException("The show has already started");
        }
        Map<Long, ShowInfo.SeatInfo> layout = new LinkedHashMap<>();
        if (show.getSeats() != null) {
            for (ShowInfo.SeatInfo seat : show.getSeats()) {
                layout.put(seat.getSeatId(), seat);
            }
        }
        Map<Long, ShowInfo.SeatInfo> selected = new LinkedHashMap<>();
        for (Long id : seatIds) {
            ShowInfo.SeatInfo seat = layout.get(id);
            if (seat == null) {
                throw new BusinessRuleException("Seat " + id + " does not belong to show " + show.getShowId());
            }
            selected.put(id, seat);
        }
        return selected;
    }

    private Optional<BookingResponse> findReplay(String customerId, String key, Long showId, Set<Long> seatIds) {
        BookingResponse found = tx.execute(status ->
                bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, key)
                        .map(existing -> {
                            Set<Long> existingSeats = new HashSet<>();
                            existing.getSeats().forEach(s -> existingSeats.add(s.getSeatId()));
                            if (!existing.getShowId().equals(showId) || !existingSeats.equals(seatIds)) {
                                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Idempotency-Key was already used for a different booking request");
                            }
                            return BookingMapper.toResponse(existing);
                        })
                        .orElse(null));
        return Optional.ofNullable(found);
    }

    // -------------------------------------------------------------------- read

    @Transactional
    public BookingResponse get(UUID id, CurrentUser user) {
        Booking booking = loadOwned(id, user);
        expireIfDue(booking);
        return BookingMapper.toResponse(booking);
    }

    @Transactional
    public PageResponse<BookingResponse> listMine(CurrentUser user, Pageable pageable) {
        String customerId = user.requireUserId();
        return PageResponse.of(bookingRepository.findByCustomerId(customerId, pageable), b -> {
            expireIfDue(b);
            return BookingMapper.toResponse(b);
        });
    }

    // ------------------------------------------------------------------ cancel

    @Transactional
    public BookingResponse cancel(UUID id, CurrentUser user) {
        Booking booking = loadOwned(id, user);
        expireIfDue(booking);
        switch (booking.getStatus()) {
            case PENDING_PAYMENT:
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setSagaState(SagaState.FAILED);
                booking.setUpdatedAt(Instant.now());
                releaseSeats(booking);
                sagaLog.record(id, SagaState.FAILED, "CancelledBeforePayment", "Customer cancelled; no money moved");
                log.info("Booking cancelled bookingId={}", id);
                return BookingMapper.toResponse(bookingRepository.save(booking));
            case CANCELLED:
                return BookingMapper.toResponse(booking); // idempotent
            case CONFIRMED:
                // S5: Phase 2 allows cancelling a CONFIRMED booking. Compensation = refund:
                // free the seats, then ask Payment (via the outbox) to refund. Booking is final
                // only when it later receives PaymentRefunded.
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setSagaState(SagaState.COMPENSATING);
                booking.setUpdatedAt(Instant.now());
                releaseSeats(booking);
                emitSagaEvent(booking, "BookingCancelled", "Customer cancelled a confirmed booking");
                sagaLog.record(id, SagaState.COMPENSATING, "BookingCancelled",
                        "Customer cancelled a confirmed booking; refund requested");
                log.info("Confirmed booking cancelled, refund requested bookingId={}", id);
                return BookingMapper.toResponse(bookingRepository.save(booking));
            default:
                throw new InvalidStateTransitionException(
                        "A booking in status " + booking.getStatus() + " cannot be cancelled");
        }
    }

    // ---------------------------------------------------------- payment result

    /**
     * Called by Payment Service (ST-02). Idempotent: repeated or late results never corrupt state.
     * Payment success alone is not the final state: the booking must still be valid (SC-12).
     */
    @Transactional
    public PaymentResultAck applyPaymentResult(UUID bookingId, PaymentResultRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        Instant now = Instant.now();
        PaymentResultOutcome outcome;

        if (request.getResult() == PaymentResult.SUCCESS) {
            switch (booking.getStatus()) {
                case PENDING_PAYMENT:
                    if (booking.isExpiredAt(now)) {
                        expire(booking);
                        outcome = PaymentResultOutcome.REJECTED_EXPIRED;
                        log.warn("PAYMENT SUCCEEDED AFTER EXPIRY bookingId={} paymentId={} - not confirmed; refund requested",
                                bookingId, request.getPaymentId());
                        rejectLatePayment(booking, "Payment arrived after the booking expired");
                    } else {
                        confirm(booking, now);
                        outcome = PaymentResultOutcome.APPLIED;
                    }
                    break;
                case CONFIRMED:
                    outcome = PaymentResultOutcome.IGNORED_DUPLICATE;
                    break;
                case EXPIRED:
                    outcome = PaymentResultOutcome.REJECTED_EXPIRED;
                    log.warn("PAYMENT SUCCEEDED FOR EXPIRED BOOKING bookingId={} paymentId={}", bookingId, request.getPaymentId());
                    rejectLatePayment(booking, "Payment arrived for an expired booking");
                    break;
                default:
                    outcome = PaymentResultOutcome.REJECTED_INVALID_STATE;
                    log.warn("Payment success for booking {} in status {} rejected", bookingId, booking.getStatus());
                    rejectLatePayment(booking, "Payment arrived but booking is " + booking.getStatus());
            }
        } else {
            switch (booking.getStatus()) {
                case PENDING_PAYMENT:
                    booking.setStatus(BookingStatus.PAYMENT_FAILED);
                    booking.setSagaState(SagaState.FAILED);
                    booking.setFailureReason(request.getReason());
                    booking.setUpdatedAt(now);
                    releaseSeats(booking);
                    sagaLog.record(bookingId, SagaState.FAILED, "PaymentFailed",
                            "Payment failed (" + request.getReason() + "); seats released; no money moved");
                    outcome = PaymentResultOutcome.APPLIED;
                    log.info("Payment failed; seats released bookingId={} reason={}", bookingId, request.getReason());
                    break;
                case PAYMENT_FAILED:
                    outcome = PaymentResultOutcome.IGNORED_DUPLICATE;
                    break;
                default:
                    outcome = PaymentResultOutcome.REJECTED_INVALID_STATE;
                    log.warn("Payment failure for booking {} in status {} ignored", bookingId, booking.getStatus());
            }
        }
        bookingRepository.save(booking);
        return new PaymentResultAck(bookingId, booking.getStatus(), outcome);
    }

    private void confirm(Booking booking, Instant now) {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);
        booking.setUpdatedAt(now);
        booking.getSeats().forEach(s -> s.setState(SeatReservationState.BOOKED));
        booking.setSagaState(SagaState.CONFIRMED);
        log.info("Booking CONFIRMED bookingId={} customer={}", booking.getId(), booking.getCustomerId());
        // S3: NOT published to Kafka here. The event is written to the outbox in THIS transaction;
        // the OutboxRelay publishes it. If Kafka is down the booking is still confirmed and the
        // email is sent later, when Kafka is back.
        BookingConfirmedEvent event = toConfirmedEvent(booking, now);
        outbox.append(event.getEventId(), booking.getId(), "BookingConfirmed", confirmedTopic, event);
        sagaLog.record(booking.getId(), SagaState.CONFIRMED, "PaymentSucceeded -> BookingConfirmed",
                "Payment received, seats booked, BookingConfirmed written to outbox");
    }

    /**
     * S5: money arrived but the booking cannot be confirmed (expired / cancelled / failed).
     * Compensation: tell Payment to refund by sending BookingRejected through the outbox.
     */
    private void rejectLatePayment(Booking booking, String reason) {
        if (booking.getSagaState() == SagaState.COMPENSATING || booking.getSagaState() == SagaState.COMPENSATED) {
            return; // refund already requested (duplicate delivery): do not ask twice
        }
        booking.setSagaState(SagaState.COMPENSATING);
        emitSagaEvent(booking, "BookingRejected", reason);
        sagaLog.record(booking.getId(), SagaState.COMPENSATING, "BookingRejected", reason + "; refund requested");
    }

    /** Builds BookingRejected / BookingCancelled with the next aggregate version and appends it to the outbox. */
    private void emitSagaEvent(Booking booking, String type, String reason) {
        booking.setAggregateVersion(booking.getAggregateVersion() + 1);
        registry.counter("tickethub_saga_compensations_total", "type", type).increment(); // ST-77
        BookingSagaEvent event = BookingSagaEvent.builder()
                // deterministic: the same booking can only be rejected/cancelled once (append is idempotent)
                .eventId(UUID.nameUUIDFromBytes((type + ":" + booking.getId()).getBytes(StandardCharsets.UTF_8)))
                .eventType(type)
                .eventVersion(1)
                .aggregateVersion(booking.getAggregateVersion())
                .occurredAt(Instant.now())
                .bookingId(booking.getId())
                .customerId(booking.getCustomerId())
                .amount(booking.getAmount())
                .currency(booking.getCurrency())
                .reason(reason)
                .build();
        outbox.append(event.getEventId(), booking.getId(), type, bookingEventsTopic, event);
    }

    /** S5: Payment says the refund is done. This is the final step of the compensation. */
    @Transactional
    public void recordRefund(UUID bookingId, UUID paymentId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("PaymentRefunded for unknown booking {}", bookingId);
            return;
        }
        booking.setSagaState(SagaState.COMPENSATED);
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);
        sagaLog.record(bookingId, SagaState.COMPENSATED, "PaymentRefunded",
                "Refund completed for payment " + paymentId + "; booking is " + booking.getStatus());
        log.info("Refund recorded bookingId={} paymentId={} status={}", bookingId, paymentId, booking.getStatus());
    }

    /**
     * S7: "where is booking X and why?" A plain-language answer plus the full timeline.
     */
    @Transactional(readOnly = true)
    public SagaView sagaView(UUID id, CurrentUser user) {
        Booking b = loadOwned(id, user);
        String explanation = switch (b.getSagaState()) {
            case PAYMENT_PENDING -> "Seats are held. Waiting for the payment result until " + b.getExpiresAt() + ".";
            case CONFIRMED -> "Payment received and the booking is confirmed. A confirmation email is sent by Notification.";
            case COMPENSATING -> "Money was taken but the booking is " + b.getStatus()
                    + ". A refund was requested from Payment and we are waiting for PaymentRefunded.";
            case COMPENSATED -> "The booking is " + b.getStatus() + " and the money has been refunded.";
            case FAILED -> "The booking ended as " + b.getStatus() + " before any money was kept. Nothing to undo.";
        };
        return SagaView.builder()
                .bookingId(b.getId())
                .status(b.getStatus())
                .sagaState(b.getSagaState())
                .explanation(explanation)
                .timeline(sagaLog.timeline(id))
                .build();
    }

    private BookingConfirmedEvent toConfirmedEvent(Booking b, Instant now) {
        return BookingConfirmedEvent.builder()
                .eventId(UUID.nameUUIDFromBytes(("BookingConfirmed:" + b.getId()).getBytes(StandardCharsets.UTF_8)))
                .eventType("BookingConfirmed")
                .eventVersion(2)
                .aggregateVersion(nextVersion(b))
                .occurredAt(now)
                .bookingId(b.getId())
                .customerId(b.getCustomerId())
                .customerEmail(b.getCustomerEmail())
                .showId(b.getShowId())
                .eventTitle(b.getEventTitle())
                .venueName(b.getVenueName())
                .showStartTime(b.getShowStartTime())
                .seatIds(b.getSeats().stream().map(BookingSeat::getSeatId).toList())
                .seatLabels(b.getSeats().stream().map(BookingSeat::label).toList())
                .amount(b.getAmount())
                .totalAmount(b.getAmount())
                .seatCategory(b.getSeats().isEmpty() ? null : b.getSeats().get(0).getCategory())
                .currency(b.getCurrency())
                .build();
    }

    /** S6: every event Booking publishes for a booking gets the next number (1, 2, 3 ...). */
    private long nextVersion(Booking b) {
        b.setAggregateVersion(b.getAggregateVersion() + 1);
        return b.getAggregateVersion();
    }

    // ----------------------------------------------------------------- expiry

    /** Expires one booking if it is still pending and overdue. Called by the sweeper (separate tx per booking). */
    @Transactional
    public boolean expireById(UUID id) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT
                || !booking.isExpiredAt(Instant.now())) {
            return false;
        }
        expire(booking);
        bookingRepository.save(booking);
        return true;
    }

    private void expireStaleReservations(Long showId) {
        List<Booking> stale = bookingRepository.findByShowIdAndStatusAndExpiresAtBefore(
                showId, BookingStatus.PENDING_PAYMENT, Instant.now());
        if (!stale.isEmpty()) {
            stale.forEach(this::expire);
            // Flush so the released seats are visible before new reservations are inserted.
            bookingRepository.saveAllAndFlush(stale);
        }
    }

    private void expireIfDue(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT && booking.isExpiredAt(Instant.now())) {
            expire(booking);
            bookingRepository.save(booking);
        }
    }

    private void expire(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setSagaState(SagaState.FAILED);
        booking.setUpdatedAt(Instant.now());
        releaseSeats(booking);
        sagaLog.record(booking.getId(), SagaState.FAILED, "Expired", "No payment before the deadline; seats released");
        log.info("Booking EXPIRED and seats released bookingId={}", booking.getId());
    }

    private void releaseSeats(Booking booking) {
        booking.getSeats().forEach(s -> s.setState(SeatReservationState.AVAILABLE));
    }

    // --------------------------------------------------------------- internal

    @Transactional(readOnly = true)
    public BookingInternalResponse internalView(UUID id) {
        return bookingRepository.findById(id)
                .map(BookingMapper::toInternal)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + id + " not found"));
    }

    /** S8: bookings confirmed in the last N minutes (max 500). Payment checks each one has a captured payment. */
    @Transactional(readOnly = true)
    public List<UUID> confirmedIdsSince(int minutes) {
        return bookingRepository.findConfirmedIdsSince(Instant.now().minus(minutes, ChronoUnit.MINUTES),
                org.springframework.data.domain.PageRequest.of(0, 500));
    }

    /** Reservation state for a show (ST-05). Seats not listed are AVAILABLE. */
    @Transactional(readOnly = true)
    public List<SeatReservationDto> seatStates(Long showId) {
        Instant now = Instant.now();
        return seatRepository.findByShowIdAndStateNot(showId, SeatReservationState.AVAILABLE).stream()
                // a reservation whose booking is overdue is as good as released, even before the sweeper runs
                .filter(s -> !(s.getState() == SeatReservationState.RESERVED && s.getBooking().isExpiredAt(now)))
                .map(s -> new SeatReservationDto(s.getSeatId(), s.getState()))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private Booking loadOwned(UUID id, CurrentUser user) {
        String userId = user.requireUserId();
        Optional<Booking> found = user.isAdmin()
                ? bookingRepository.findById(id)
                : bookingRepository.findByIdAndCustomerId(id, userId);
        return found.orElseThrow(() -> new ResourceNotFoundException("Booking " + id + " not found"));
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > 100) {
            throw new BusinessRuleException("Idempotency-Key must be at most 100 characters");
        }
        return key;
    }

    private boolean hasConstraint(Throwable error, String constraintName) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraintName)) {
                return true;
            }
        }
        return false;
    }
}
