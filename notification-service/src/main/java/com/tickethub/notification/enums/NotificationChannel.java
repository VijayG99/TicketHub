package com.tickethub.notification.enums;

/**
 * Delivery channels (ST-79). EMAIL goes through SMTP; PUSH is a second, independent channel
 * (an app/browser push, simulated here by a logging sender, addressed to the customer id so no
 * phone number or device token needs to be stored). Each channel has its own notification row,
 * its own status, its own circuit breaker, and fails independently of the other.
 */
public enum NotificationChannel {
    EMAIL,
    PUSH
}
