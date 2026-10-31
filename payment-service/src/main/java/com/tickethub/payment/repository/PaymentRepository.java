package com.tickethub.payment.repository;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.enums.PaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Ownership enforced in the query (ST-59). */
    Optional<Payment> findByIdAndCustomerId(UUID id, String customerId);

    Optional<Payment> findFirstByBookingIdAndStatusIn(UUID bookingId, Collection<PaymentStatus> statuses);

    Optional<Payment> findByProviderReference(String providerReference);

    List<Payment> findByBookingNotifiedFalseAndStatusInAndSyncAttemptsLessThan(
            Collection<PaymentStatus> statuses, int maxAttempts, Pageable pageable);

    /** S8: payments sitting in a status longer than a grace period (e.g. UNKNOWN, PENDING, SUCCESS). */
    List<Payment> findByStatusInAndUpdatedAtBefore(Collection<PaymentStatus> statuses, Instant before, Pageable pageable);
}
