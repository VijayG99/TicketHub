package com.tickethub.notification.sender;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development sender (ST-57): logs the message instead of sending it. */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.sender", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String send(Notification n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new NotificationDeliveryException("No recipient available for booking " + n.getBookingId());
        }
        log.info("[DEV EMAIL] to={} subject=\"{}\" bookingId={}", n.getRecipient(), n.getSubject(), n.getBookingId());
        return "LOG-" + UUID.randomUUID();
    }
}
