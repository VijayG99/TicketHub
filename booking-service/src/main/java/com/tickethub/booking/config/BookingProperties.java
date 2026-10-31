package com.tickethub.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {
    /** How long a PENDING_PAYMENT booking holds its seats (ST-09: configurable). */
    private int reservationExpiryMinutes = 10;
    private int maxSeatsPerBooking = 6;
    private long expirySweepIntervalMs = 15000;
}
