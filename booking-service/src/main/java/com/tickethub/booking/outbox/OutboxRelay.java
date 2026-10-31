package com.tickethub.booking.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S3: the TRANSACTIONAL OUTBOX (publish side), a.k.a. "relay" / "message relay".
 *
 * Every second it:
 *   1. locks a small batch of unpublished rows (SELECT ... FOR UPDATE SKIP LOCKED, oldest first),
 *   2. sends each to Kafka and WAITS for the broker's acknowledgement,
 *   3. marks the row published.
 * If Kafka is down the send fails, the row stays unpublished and is retried on the next tick.
 * We STOP at the first failure so events keep their order.
 *
 * Delivery is AT-LEAST-ONCE: a crash between step 2 and 3 publishes the row again.
 * That is fine because every consumer has an inbox (S2) and ignores duplicates.
 *
 * SKIP LOCKED lets two Booking instances run the relay at the same time without sending the same row twice
 * (in the normal case) and without waiting for each other.
 *
 * Big systems replace this poller with CDC (Debezium reads the database log and publishes).
 * Same idea, no polling.
 */
@Slf4j
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate tx;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafkaTemplate,
                       PlatformTransactionManager txManager, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.tx = new TransactionTemplate(txManager);
        // visible in Prometheus/Grafana: if this number keeps growing, Kafka (or the relay) is stuck
        Gauge.builder("tickethub_outbox_unpublished", this, OutboxRelay::countUnpublished)
                .description("Outbox rows not yet published to Kafka")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${booking.outbox.relay-interval-ms:1000}")
    public void relay() {
        try {
            Integer sent = tx.execute(status -> publishBatch());
            if (sent != null && sent > 0) {
                log.info("Outbox relay published {} event(s)", sent);
            }
        } catch (Exception e) {
            log.warn("Outbox relay run failed: {}", e.toString());
        }
    }

    private int publishBatch() {
        List<Row> rows = jdbc.query(
                "SELECT id, topic, kafka_key, payload FROM outbox_events "
                        + "WHERE published_at IS NULL ORDER BY seq LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, i) -> new Row((UUID) rs.getObject("id"), rs.getString("topic"),
                        rs.getString("kafka_key"), rs.getString("payload")),
                BATCH_SIZE);
        int sent = 0;
        for (Row r : rows) {
            try {
                kafkaTemplate.send(r.topic(), r.key(), r.payload()).get(5, TimeUnit.SECONDS);
                jdbc.update("UPDATE outbox_events SET published_at = now() WHERE id = ?", r.id());
                sent++;
            } catch (Exception e) {
                jdbc.update("UPDATE outbox_events SET attempts = attempts + 1 WHERE id = ?", r.id());
                log.warn("Outbox publish failed id={} topic={} (will retry): {}", r.id(), r.topic(), e.toString());
                break; // keep order: do not publish later rows before this one
            }
        }
        return sent;
    }

    double countUnpublished() {
        try {
            Long n = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return -1;
        }
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }
}
