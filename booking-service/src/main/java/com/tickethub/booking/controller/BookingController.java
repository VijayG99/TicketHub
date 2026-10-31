package com.tickethub.booking.controller;

import com.tickethub.booking.common.security.CurrentUser;
import com.tickethub.booking.common.web.PageResponse;
import com.tickethub.booking.dto.BookingCreationResult;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingRequest;
import com.tickethub.booking.service.BookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookings")
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** 201 for a new booking, 200 when an idempotent retry returns the original (SC-13). */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            CurrentUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingCreationResult result = bookingService.create(user, request, idempotencyKey);
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.getBooking());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id, CurrentUser user) {
        return bookingService.get(id, user);
    }

    @GetMapping
    public PageResponse<BookingResponse> listMine(
            CurrentUser user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.listMine(user, pageable);
    }

    /** S7: "where is my booking and why?" - saga state, plain-language explanation and timeline. */
    @GetMapping("/{id}/saga")
    public com.tickethub.booking.dto.SagaView saga(@PathVariable UUID id, CurrentUser user) {
        return bookingService.sagaView(id, user);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, CurrentUser user) {
        return bookingService.cancel(id, user);
    }
}
