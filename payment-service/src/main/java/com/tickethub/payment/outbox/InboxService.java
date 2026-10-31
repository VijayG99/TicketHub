package com.tickethub.payment.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2/S4: the INBOX (idempotent consumer). Same idea as in Notification.
 * The primary key on event_id lets exactly one delivery of an event "win"; it runs inside the
 * same transaction as the business change, so "marked as handled" and "handled" are atomic.
 */
@Service
@RequiredArgsConstructor
public class InboxService {

    private final JdbcTemplate jdbc;

    /** @return true if this is the first time we see the event, false for a duplicate. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId, String eventType) {
        return jdbc.update("INSERT INTO inbox_events (event_id, event_type, received_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (event_id) DO NOTHING", eventId, eventType, Timestamp.from(Instant.now())) == 1;
    }
}
