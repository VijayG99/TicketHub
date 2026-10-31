package com.tickethub.notification.sender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ST-79: the second channel. */
class PushNotificationSenderTest {

    private final PushNotificationSender sender = new PushNotificationSender();

    @Test
    void declaresItsChannel() {
        assertEquals(NotificationChannel.PUSH, sender.channel());
    }

    @Test
    void sendsToACustomerId() {
        Notification n = new Notification();
        n.setBookingId(UUID.randomUUID());
        n.setRecipient("alice");
        n.setSubject("Booking confirmed");
        assertTrue(sender.send(n).startsWith("PUSH-"));
    }

    @Test
    void refusesWhenThereIsNoRecipient() {
        Notification n = new Notification();
        n.setBookingId(UUID.randomUUID());
        assertThrows(NotificationDeliveryException.class, () -> sender.send(n));
    }
}
