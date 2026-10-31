package com.tickethub.notification.sender;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;

/** Delivery seam: one implementation per CHANNEL (email, push ...). */
public interface NotificationSender {

    /** The channel this sender delivers on. */
    NotificationChannel channel();

    /** @return a provider reference (e.g. SMTP message id) @throws NotificationDeliveryException on failure */
    String send(Notification notification);
}
