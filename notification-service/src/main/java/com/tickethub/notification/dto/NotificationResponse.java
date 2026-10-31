package com.tickethub.notification.dto;

import com.tickethub.notification.enums.NotificationChannel;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.enums.NotificationType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationResponse {
    private final UUID notificationId;
    private final UUID bookingId;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final String recipient;
    private final String subject;
    private final NotificationStatus status;
    private final String providerReference;
    private final String failureReason;
    private final int attempts;
    private final Instant createdAt;
    private final Instant sentAt;
}
