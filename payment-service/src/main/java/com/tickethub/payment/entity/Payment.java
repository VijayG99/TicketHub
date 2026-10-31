package com.tickethub.payment.entity;

import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.enums.PaymentProviderName;
import com.tickethub.payment.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    /** Taken from the booking snapshot at initiation; never from the client (ST-46, ST-54). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProviderName provider;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    /** True once Booking has acknowledged the final result (SC-06 recovery). */
    @Column(name = "booking_notified", nullable = false)
    private boolean bookingNotified;

    @Column(name = "sync_attempts", nullable = false)
    private int syncAttempts;

    /** S5: the provider's refund id once the payment has been refunded. */
    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    /** S6: newest Booking event version applied to this payment; older or equal ones are ignored. */
    @Column(name = "last_booking_event_version", nullable = false)
    private long lastBookingEventVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
