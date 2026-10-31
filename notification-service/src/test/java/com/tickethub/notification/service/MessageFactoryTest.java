package com.tickethub.notification.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickethub.notification.event.BookingConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageFactoryTest {

    @Test
    void bodyContainsBookingDetails() {
        BookingConfirmedEvent e = new BookingConfirmedEvent();
        e.setBookingId(UUID.randomUUID());
        e.setEventTitle("Space Odyssey");
        e.setVenueName("Grand Cinema");
        e.setShowStartTime(Instant.parse("2030-01-01T18:30:00Z"));
        e.setSeatLabels(List.of("A1", "A2"));
        e.setAmount(new BigDecimal("500.00"));
        e.setCurrency("INR");

        MessageFactory factory = new MessageFactory();
        String body = factory.body(e);
        assertTrue(factory.subject(e).contains("Space Odyssey"));
        assertTrue(body.contains("A1, A2"));
        assertTrue(body.contains("Grand Cinema"));
        assertTrue(body.contains("500.00 INR"));
        assertTrue(body.contains(e.getBookingId().toString()));
    }
}
