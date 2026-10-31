package com.tickethub.notification.service;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tiny read helper so NotificationService does not touch the repository directly. */
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAccess {

    private final NotificationRepository repository;

    public Notification find(UUID id) {
        return repository.findById(id).orElse(null);
    }
}
