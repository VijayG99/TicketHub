package com.tickethub.booking.mapper;

import com.tickethub.booking.dto.BookingInternalResponse;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.BookingSeatResponse;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking b) {
        return BookingResponse.builder()
                .bookingId(b.getId())
                .showId(b.getShowId())
                .eventTitle(b.getEventTitle())
                .venueName(b.getVenueName())
                .showStartTime(b.getShowStartTime())
                .seats(b.getSeats().stream().map(BookingMapper::toSeat).toList())
                .amount(b.getAmount())
                .currency(b.getCurrency())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .expiresAt(b.getExpiresAt())
                .confirmedAt(b.getConfirmedAt())
                .build();
    }

    public static BookingInternalResponse toInternal(Booking b) {
        return BookingInternalResponse.builder()
                .bookingId(b.getId())
                .customerId(b.getCustomerId())
                .showId(b.getShowId())
                .amount(b.getAmount())
                .currency(b.getCurrency())
                .status(b.getStatus())
                .expiresAt(b.getExpiresAt())
                .build();
    }

    private static BookingSeatResponse toSeat(BookingSeat s) {
        return BookingSeatResponse.builder()
                .seatId(s.getSeatId())
                .label(s.label())
                .rowLabel(s.getRowLabel())
                .seatNumber(s.getSeatNumber())
                .category(s.getCategory())
                .state(s.getState())
                .build();
    }
}
