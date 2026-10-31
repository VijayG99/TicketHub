package com.tickethub.notification.sender;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ST-79: the SECOND notification channel. A simulated push provider: it logs the push instead of
 * calling Firebase/APNs. A real provider would replace the body of {@link #send}; nothing else changes
 * because the rest of the service only knows the {@link NotificationSender} interface.
 * Switch off with notification.push.enabled=false (then no PUSH rows are created at all).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true", matchIfMissing = true)
public class PushNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public String send(Notification n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new NotificationDeliveryException("No push recipient for booking " + n.getBookingId());
        }
        log.info("[PUSH] to user={} title=\"{}\" bookingId={}", n.getRecipient(), n.getSubject(), n.getBookingId());
        return "PUSH-" + UUID.randomUUID();
    }
}
