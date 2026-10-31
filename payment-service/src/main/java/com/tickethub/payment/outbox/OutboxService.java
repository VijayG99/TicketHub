package com.tickethub.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S3: the TRANSACTIONAL OUTBOX (write side).
 *
 * Problem: "save booking to DB" and "publish to Kafka" cannot be one transaction. If we publish
 * after commit and Kafka is down (or we crash), the booking is confirmed but nobody is told.
 *
 * Fix: do not talk to Kafka here. Instead INSERT the event into the outbox_events table, in the
 * SAME database transaction as the booking change. Both commit together or neither does.
 * {@link OutboxRelay} publishes the rows to Kafka afterwards, retrying until it works.
 *
 * Propagation.MANDATORY = this method refuses to run outside a transaction, so nobody can
 * accidentally append an event without it being atomic with the state change.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /**
     * @param eventId   becomes the row id; ON CONFLICT DO NOTHING makes appending the same event twice harmless
     * Kafka message key = booking id, so all events of one booking share a partition (S6)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, UUID bookingId, String eventType, String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + eventType, e);
        }
        jdbc.update("INSERT INTO outbox_events (id, aggregate_id, event_type, topic, kafka_key, payload, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                eventId, bookingId, eventType, topic, bookingId.toString(), json, Timestamp.from(Instant.now()));
    }
}
