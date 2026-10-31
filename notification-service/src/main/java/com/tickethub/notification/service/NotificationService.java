package com.tickethub.notification.service;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.sender.EmailDeliveryGuard;
import com.tickethub.notification.sender.NotificationDeliveryException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The brain of Notification: turns one BookingConfirmed event into at most one email.
 *
 * <pre>
 *  handle(event)
 *    1. validate            bad data  -> IllegalArgumentException -> NOT retried -> dead-letter topic (S1)
 *    2. recorder.accept     inbox + one notification row PER CHANNEL (email, push) in one transaction (S2, ST-79)
 *         duplicate?  SENT/REPLAYED -> stop.  PENDING/RETRYING/DEAD -> go on and try to send again
 *    3. deliver             send through circuit breaker + rate limiter (S10)
 *         ok      -> SENT
 *         failed  -> RETRYING + throw -> Kafka moves the message to the next retry topic
 * </pre>
 *
 * Decision for "duplicate whose first attempt never finished sending" (S2 task 4):
 * we DO try again, because the notification is still PENDING/RETRYING. The only unavoidable gap is a
 * crash in the few milliseconds AFTER the SMTP server accepted the mail but BEFORE we saved SENT:
 * the retry would then send a second email. That is the classic at-least-once trade-off; real providers
 * close it with an idempotency key on their API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRecorder recorder;
    private final NotificationRepositoryAccess repositoryAccess;
    private final EmailDeliveryGuard guard;

    /** S12: true on the replay consumer. It rebuilds records but never sends. */
    @Value("${notification.replay-mode:false}")
    private boolean replayMode;

    /** S11 lab: artificial slowness per message so consumer lag can be observed. 0 = off. */
    @Value("${notification.simulated-delay-ms:0}")
    private long simulatedDelayMs;

    public void handle(BookingConfirmedEvent event) {
        validate(event);

        NotificationRecorder.Accepted accepted;
        try {
            accepted = recorder.accept(event, replayMode);
        } catch (DataIntegrityViolationException e) {
            // Same booking already has a confirmation under a DIFFERENT event id (unique booking+type).
            // The whole transaction rolled back (inbox row too). Nothing more to do for this event.
            log.warn("Booking {} already has a confirmation notification; ignoring eventId={}",
                    event.getBookingId(), event.getEventId());
            return;
        }

        List<Notification> notifications = accepted.notifications();
        if (accepted.duplicate()) {
            // Only rows that are not finished need work. Finished ones (SENT/REPLAYED) are skipped inside deliver().
            log.info("Duplicate eventId={} bookingId={}: re-checking {} notification(s)",
                    event.getEventId(), event.getBookingId(), notifications.size());
        }
        if (replayMode) {
            log.info("[REPLAY] record(s) rebuilt for bookingId={}, nothing sent", event.getBookingId());
            return;
        }
        // Every channel is attempted even if an earlier one fails (an SMTP outage must not stop the push).
        // The FIRST failure is rethrown afterwards so Kafka moves the message to a retry topic; on the retry,
        // channels that already succeeded are SENT and are skipped, so nothing is sent twice.
        RuntimeException firstFailure = null;
        for (Notification n : notifications) {
            try {
                deliver(n.getId());
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * Sends one notification and records the outcome.
     * On failure it records RETRYING and THROWS, so the Kafka retry-topic machinery redelivers later.
     */
    public void deliver(UUID notificationId) {
        Notification n = repositoryAccess.find(notificationId);
        if (n == null || isFinished(n.getStatus())) {
            return;
        }
        sleepIfSimulating();
        try {
            String providerReference = n.getChannel() == NotificationChannel.PUSH ? guard.sendPush(n) : guard.sendEmail(n);
            recorder.markSent(n.getId(), providerReference);
            log.info("Notification SENT id={} bookingId={}", n.getId(), n.getBookingId());
        } catch (Exception e) {
            recorder.markRetrying(n.getId(), e.getMessage());
            log.warn("Notification delivery failed id={} bookingId={} attempt={} reason={}",
                    n.getId(), n.getBookingId(), n.getAttempts() + 1, e.getMessage());
            throw new NotificationDeliveryException("Delivery failed: " + e.getMessage(), e);
        }
    }

    private static void validate(BookingConfirmedEvent e) {
        if (e.getEventId() == null || e.getBookingId() == null) {
            throw new IllegalArgumentException("BookingConfirmed event is missing eventId or bookingId");
        }
        if (e.getCustomerEmail() == null || e.getCustomerEmail().isBlank()) {
            // Retrying can never fix a missing email, so this is "bad data": straight to the DLT.
            throw new IllegalArgumentException("BookingConfirmed event has no customerEmail (bookingId=" + e.getBookingId() + ")");
        }
    }

    private static boolean isFinished(NotificationStatus s) {
        // DEAD is deliberately NOT finished: an operator replaying a dead letter wants delivery to be attempted again.
        return s == NotificationStatus.SENT || s == NotificationStatus.REPLAYED;
    }

    private void sleepIfSimulating() {
        if (simulatedDelayMs > 0) {
            try {
                Thread.sleep(simulatedDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
