package com.tickethub.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processed_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
