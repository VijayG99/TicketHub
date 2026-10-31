package com.tickethub.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phase 2 (S1): a Kafka message that could not be processed, parked in the database.
 * The same message also sits in the Kafka "-dlt" topic; this table makes it easy to list,
 * inspect and replay through the REST API without Kafka tooling.
 * Status: PARKED (waiting for a human) -> REPLAYED (sent back to the main topic).
 */
@Entity
@Table(name = "dead_letters")
@Getter
@Setter
@NoArgsConstructor
public class DeadLetter {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "kafka_key", length = 200)
    private String kafkaKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(length = 1000)
    private String error;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
