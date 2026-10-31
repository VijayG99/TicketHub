package com.tickethub.booking.entity;

import com.tickethub.booking.common.enums.CurrencyCode;
import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.enums.SagaState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "customer_email", length = 200)
    private String customerEmail;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "show_start_time", nullable = false)
    private Instant showStartTime;

    /** Snapshot: show price x number of seats at creation. Later price changes never alter it (ST-46). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** S6: counter stamped on every event Booking publishes for this booking (1, 2, 3 ...). */
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    /** S6: newest Payment event version already applied; older or equal ones are ignored. */
    @Column(name = "last_payment_event_version", nullable = false)
    private long lastPaymentEventVersion;

    /** S7: saga progress (see {@link SagaState}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "saga_state", nullable = false, length = 20)
    private SagaState sagaState = SagaState.PAYMENT_PENDING;

    @Version
    private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> seats = new ArrayList<>();

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
