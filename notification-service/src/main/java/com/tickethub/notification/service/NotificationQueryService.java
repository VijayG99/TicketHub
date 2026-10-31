package com.tickethub.notification.service;

import com.tickethub.notification.common.exception.ResourceNotFoundException;
import com.tickethub.notification.common.web.PageResponse;
import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only operational visibility ("booking succeeded but the email is missing", Blueprint section 15). */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID bookingId, NotificationStatus status, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (bookingId != null) {
                predicates.add(cb.equal(root.get("bookingId"), bookingId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.of(repository.findAll(spec, pageable), NotificationQueryService::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID id) {
        return repository.findById(id).map(NotificationQueryService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " not found"));
    }

    private static NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getId())
                .bookingId(n.getBookingId())
                .type(n.getType())
                .channel(n.getChannel())
                .recipient(n.getRecipient())
                .subject(n.getSubject())
                .status(n.getStatus())
                .providerReference(n.getProviderReference())
                .failureReason(n.getFailureReason())
                .attempts(n.getAttempts())
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
