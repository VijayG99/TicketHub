package com.tickethub.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * S13: the consumer must read v1 AND v2 payloads, and must ignore fields it does not know.
 * (Sample payloads are the ones Booking publishes, trimmed to the interesting fields.)
 */
class BookingConfirmedEventContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true); // strict: only @JsonIgnoreProperties saves us

    private static final String V1 = """
            {"eventId":"11111111-1111-4111-a111-111111111111","eventType":"BookingConfirmed",
             "bookingId":"22222222-2222-4222-a222-222222222222","customerEmail":"a@b.c",
             "eventTitle":"Old","venueName":"V","seatLabels":["A1"],"amount":100.00,"currency":"INR"}""";

    private static final String V2 = """
            {"eventId":"11111111-1111-4111-a111-111111111111","eventType":"BookingConfirmed","eventVersion":2,
             "aggregateVersion":1,"bookingId":"22222222-2222-4222-a222-222222222222","customerEmail":"a@b.c",
             "eventTitle":"New","venueName":"V","seatLabels":["A1"],"amount":100.00,"totalAmount":100.00,
             "seatCategory":"VIP","currency":"INR","fieldFromTheFuture":"ignored"}""";

    @Test
    void readsVersion1() throws Exception {
        BookingConfirmedEvent e = mapper.readValue(V1, BookingConfirmedEvent.class);
        assertNotNull(e.getEventId());
        assertEquals(0, new BigDecimal("100.00").compareTo(e.effectiveAmount()));
    }

    @Test
    void readsVersion2AndIgnoresUnknownFields() throws Exception {
        BookingConfirmedEvent e = mapper.readValue(V2, BookingConfirmedEvent.class);
        assertEquals(2, e.getEventVersion());
        assertEquals("VIP", e.getSeatCategory());
        assertEquals(0, new BigDecimal("100.00").compareTo(e.effectiveAmount()));
    }
}
