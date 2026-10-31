package com.tickethub.event.controller;

import com.tickethub.event.common.security.AdminOnly;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.SeatLayoutRequest;
import com.tickethub.event.dto.VenueRequest;
import com.tickethub.event.dto.VenueResponse;
import com.tickethub.event.dto.VenueSeatResponse;
import com.tickethub.event.service.VenueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Venues")
@AdminOnly
@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@Valid @RequestBody VenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable Long id) {
        return venueService.get(id);
    }

    @GetMapping
    public PageResponse<VenueResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return venueService.list(pageable);
    }

    @PostMapping("/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<VenueSeatResponse> addSeatRows(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest request) {
        return venueService.addSeatRows(id, request);
    }

    @GetMapping("/{id}/seats")
    public List<VenueSeatResponse> getSeats(@PathVariable Long id) {
        return venueService.getSeats(id);
    }
}
