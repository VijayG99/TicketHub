package com.tickethub.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The inbox table (S2: idempotent consumer).
 *
 * "INSERT ... ON CONFLICT DO NOTHING" returns 1 if WE inserted the row and 0 if the event id
 * already existed. The database decides atomically, so two consumer instances racing on the same
 * event cannot both win. A Java "SELECT then INSERT" could let both through.
 *
 * It uses JdbcTemplate (not JPA) so we can read the affected-row count directly. JdbcTemplate joins
 * the surrounding @Transactional transaction, so the inbox row and the notification row are saved
 * together or not at all.
 */
@Repository
@RequiredArgsConstructor
public class InboxRepository {

    private final JdbcTemplate jdbc;

    /** @return true if this is the first time we see the event, false if it is a duplicate. */
    public boolean insertIfAbsent(UUID eventId, String eventType, UUID bookingId) {
        int rows = jdbc.update(
                "INSERT INTO inbox_events (event_id, event_type, booking_id, received_at) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                eventId, eventType, bookingId, Timestamp.from(Instant.now()));
        return rows == 1;
    }
}
