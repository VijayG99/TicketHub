package com.tickethub.event.mapper;

import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.dto.SeatInfo;
import com.tickethub.event.dto.ShowResponse;
import com.tickethub.event.dto.VenueResponse;
import com.tickethub.event.dto.VenueSeatResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.entity.Show;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.entity.VenueSeat;

/** Entities never leave the service; controllers return these DTOs (ST-02). */
public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static EventResponse toResponse(Event e) {
        return EventResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .type(e.getType())
                .status(e.getStatus())
                .posterUrl(e.getPosterUrl())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static VenueResponse toResponse(Venue v) {
        return VenueResponse.builder()
                .id(v.getId())
                .name(v.getName())
                .city(v.getCity())
                .address(v.getAddress())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .build();
    }

    public static VenueSeatResponse toSeatResponse(VenueSeat s) {
        return VenueSeatResponse.builder()
                .seatId(s.getId())
                .rowLabel(s.getRowLabel())
                .seatNumber(s.getSeatNumber())
                .category(s.getCategory())
                .build();
    }

    public static SeatInfo toSeatInfo(VenueSeat s) {
        return SeatInfo.builder()
                .seatId(s.getId())
                .rowLabel(s.getRowLabel())
                .seatNumber(s.getSeatNumber())
                .category(s.getCategory())
                .build();
    }

    public static ShowResponse toResponse(Show s) {
        return ShowResponse.builder()
                .id(s.getId())
                .eventId(s.getEvent().getId())
                .eventTitle(s.getEvent().getTitle())
                .venueId(s.getVenue().getId())
                .venueName(s.getVenue().getName())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .ticketPrice(s.getTicketPrice())
                .currency(s.getCurrency())
                .status(s.getStatus())
                .build();
    }
}
