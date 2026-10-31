package com.tickethub.notification.repository;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    boolean existsBySourceEventId(UUID sourceEventId);

    /** One row per channel, so an event can have several. */
    List<Notification> findBySourceEventId(UUID sourceEventId);

    Page<Notification> findByBookingId(UUID bookingId, Pageable pageable);
}
