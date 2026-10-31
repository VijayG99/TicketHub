package com.tickethub.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payment's own view of the events Booking publishes on "booking-events". Unknown fields are ignored (S13). */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingEvent {
    private UUID eventId;
    private String eventType;
    private Integer eventVersion;
    private Long aggregateVersion;
    private UUID bookingId;
    private String reason;
}
