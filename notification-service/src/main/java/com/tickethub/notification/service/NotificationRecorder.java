package com.tickethub.notification.service;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.enums.NotificationType;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.repository.InboxRepository;
import com.tickethub.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short database transactions that record notification state.
 *
 * <h3>S2: the inbox pattern</h3>
 * {@link #accept} writes the inbox row AND the notification row in ONE transaction.
 * Either both exist or neither does. Consequences:
 * <ul>
 *   <li>Inbox row present => a notification row exists for it. No half state.</li>
 *   <li>A crash before commit => nothing saved => Kafka redelivers => we start clean.</li>
 *   <li>A second copy of the event => inbox insert returns "already there" => no second notification.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRecorder {

    /**
     * Result of {@link #accept}: one notification per channel. {@code duplicate=true} means the event was
     * seen before and these are the rows created the first time.
     */
    public record Accepted(List<Notification> notifications, boolean duplicate) {
    }

    private final NotificationRepository repository;
    private final InboxRepository inbox;
    private final MessageFactory messageFactory;
    private final io.micrometer.core.instrument.MeterRegistry registry;

    /** ST-79: create a PUSH notification next to the EMAIL one. */
    @Value("${notification.push.enabled:true}")
    private boolean pushEnabled;

    /**
     * @param replay true on a replay consumer (S12): the notification is stored as REPLAYED and will not be sent.
     */
    @Transactional
    public Accepted accept(BookingConfirmedEvent event, boolean replay) {
        boolean first = inbox.insertIfAbsent(event.getEventId(), "BookingConfirmed", event.getBookingId());
        if (!first) {
            // Seen before. Return the notification created the first time so the caller can decide
            // whether the earlier attempt actually finished (SENT) or still needs delivering.
            return new Accepted(repository.findBySourceEventId(event.getEventId()), true);
        }
        Instant now = Instant.now();
        NotificationStatus initial = replay ? NotificationStatus.REPLAYED : NotificationStatus.PENDING;
        List<Notification> created = new ArrayList<>();

        // channel 1: EMAIL (always)
        created.add(build(event, NotificationChannel.EMAIL, event.getCustomerEmail(),
                messageFactory.subject(event), messageFactory.body(event), initial, now));
        // channel 2: PUSH (ST-79), addressed to the customer id; skipped when disabled or when there is no customer id
        if (pushEnabled && event.getCustomerId() != null && !event.getCustomerId().isBlank()) {
            created.add(build(event, NotificationChannel.PUSH, event.getCustomerId(),
                    messageFactory.pushTitle(event), messageFactory.pushBody(event), initial, now));
        }
        // saveAllAndFlush so a unique-constraint violation (same booking, different event id)
        // surfaces HERE and rolls back the inbox row too.
        return new Accepted(repository.saveAllAndFlush(created), false);
    }

    private Notification build(BookingConfirmedEvent event, NotificationChannel channel, String recipient,
                               String subject, String body, NotificationStatus status, Instant now) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setSourceEventId(event.getEventId());
        n.setBookingId(event.getBookingId());
        n.setType(NotificationType.BOOKING_CONFIRMATION);
        n.setChannel(channel);
        n.setRecipient(recipient);
        n.setSubject(subject);
        n.setBody(body);
        n.setStatus(status);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return n;
    }

    @Transactional
    public void markSent(UUID id, String providerReference) {
        Notification n = repository.findById(id).orElseThrow();
        n.setStatus(NotificationStatus.SENT);
        n.setProviderReference(providerReference);
        n.setFailureReason(null);
        n.setAttempts(n.getAttempts() + 1);
        n.setSentAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        repository.save(n);
        // ST-77: delivery outcome per channel, graphed in Grafana
        registry.counter("tickethub_notifications_total", "channel", n.getChannel().name(), "result", "sent").increment();
    }

    /** Provider failed: keep the notification as RETRYING; a Kafka retry topic will redeliver the event (S10). */
    @Transactional
    public void markRetrying(UUID id, String reason) {
        Notification n = repository.findById(id).orElseThrow();
        n.setStatus(NotificationStatus.RETRYING);
        n.setFailureReason(truncate(reason));
        n.setAttempts(n.getAttempts() + 1);
        n.setUpdatedAt(Instant.now());
        repository.save(n);
        registry.counter("tickethub_notifications_total", "channel", n.getChannel().name(), "result", "retrying").increment();
    }

    /** All retries used up (S1/S10): the message is in the dead-letter topic. */
    @Transactional
    public void markDeadBySourceEvent(UUID sourceEventId, String reason) {
        for (Notification n : repository.findBySourceEventId(sourceEventId)) {
            if (n.getStatus() != NotificationStatus.SENT) {
                n.setStatus(NotificationStatus.DEAD);
                n.setFailureReason(truncate(reason));
                n.setUpdatedAt(Instant.now());
                repository.save(n);
            }
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) : s;
    }
}
