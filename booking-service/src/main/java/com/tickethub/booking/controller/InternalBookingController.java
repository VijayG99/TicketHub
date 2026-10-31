package com.tickethub.booking.controller;

import com.tickethub.booking.dto.BookingInternalResponse;
import com.tickethub.booking.dto.PaymentResultAck;
import com.tickethub.booking.dto.PaymentResultRequest;
import com.tickethub.booking.dto.SeatReservationDto;
import com.tickethub.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API for Event/Show and Payment. The gateway never routes /internal/**. */
@Hidden
@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
public class InternalBookingController {

    private final BookingService bookingService;

    @GetMapping("/{id}")
    public BookingInternalResponse get(@PathVariable UUID id) {
        return bookingService.internalView(id);
    }

    @PostMapping("/{id}/payment-result")
    public PaymentResultAck paymentResult(@PathVariable UUID id, @Valid @RequestBody PaymentResultRequest request) {
        return bookingService.applyPaymentResult(id, request);
    }

    /** S8: used by Payment's reconciliation job. */
    @GetMapping("/confirmed-ids")
    public List<UUID> confirmedIds(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "60") int sinceMinutes) {
        return bookingService.confirmedIdsSince(sinceMinutes);
    }

    @GetMapping("/shows/{showId}/seat-states")
    public List<SeatReservationDto> seatStates(@PathVariable Long showId) {
        return bookingService.seatStates(showId);
    }
}
