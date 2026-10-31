package com.tickethub.notification.service;

import com.tickethub.notification.event.BookingConfirmedEvent;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MessageFactory {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public String subject(BookingConfirmedEvent e) {
        return "Booking confirmed: " + e.getEventTitle();
    }

    public String body(BookingConfirmedEvent e) {
        String seats = e.getSeatLabels() == null ? "-" : String.join(", ", e.getSeatLabels());
        String when = e.getShowStartTime() == null ? "-" : WHEN.format(e.getShowStartTime());
        return "Your booking is confirmed.\n\n"
                + "Event:      " + e.getEventTitle() + "\n"
                + "Venue:      " + e.getVenueName() + "\n"
                + "Show time:  " + when + "\n"
                + "Seats:      " + seats + "\n"
                + "Amount:     " + e.effectiveAmount() + " " + e.getCurrency() + "\n"
                + "Booking id: " + e.getBookingId() + "\n\n"
                + "Thank you for booking with TicketHub.\n";
    }

    /** ST-79: short text for the PUSH channel. */
    public String pushTitle(BookingConfirmedEvent e) {
        return "Booking confirmed";
    }

    public String pushBody(BookingConfirmedEvent e) {
        String seats = e.getSeatLabels() == null ? "-" : String.join(", ", e.getSeatLabels());
        return e.getEventTitle() + " at " + e.getVenueName() + ", seats " + seats;
    }
}
