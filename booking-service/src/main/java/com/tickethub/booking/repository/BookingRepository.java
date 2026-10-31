package com.tickethub.booking.repository;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.enums.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Ownership is enforced in the query itself (ST-59). */
    Optional<Booking> findByIdAndCustomerId(UUID id, String customerId);

    Optional<Booking> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    Page<Booking> findByCustomerId(String customerId, Pageable pageable);

    List<Booking> findByShowIdAndStatusAndExpiresAtBefore(Long showId, BookingStatus status, Instant before);

    @Query("select b.id from Booking b where b.status = :status and b.expiresAt < :now order by b.expiresAt")
    List<UUID> findDueIds(@Param("status") BookingStatus status, @Param("now") Instant now, Pageable pageable);

    /** S8: ids of bookings confirmed since a point in time (used by Payment's reconciliation). */
    @Query("select b.id from Booking b where b.status = com.tickethub.booking.enums.BookingStatus.CONFIRMED and b.confirmedAt >= :since order by b.confirmedAt")
    List<UUID> findConfirmedIdsSince(@Param("since") Instant since, Pageable pageable);
}
