package com.tickethub.event.controller;

import com.tickethub.event.common.security.AdminOnly;
import com.tickethub.event.common.security.CurrentUser;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.SeatAvailabilityResponse;
import com.tickethub.event.dto.ShowRequest;
import com.tickethub.event.dto.ShowResponse;
import com.tickethub.event.dto.ShowStatusRequest;
import com.tickethub.event.enums.ShowStatus;
import com.tickethub.event.service.ShowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shows")
@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @GetMapping
    public PageResponse<ShowResponse> list(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) ShowStatus status,
            CurrentUser user,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return showService.list(eventId, status, user, pageable);
    }

    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable Long id, CurrentUser user) {
        return showService.get(id, user);
    }

    @GetMapping("/{showId}/seats")
    public SeatAvailabilityResponse seats(@PathVariable Long showId, CurrentUser user) {
        return showService.seatAvailability(showId, user);
    }

    @AdminOnly
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody ShowRequest request) {
        return showService.create(request);
    }

    @AdminOnly
    @PutMapping("/{id}")
    public ShowResponse update(@PathVariable Long id, @Valid @RequestBody ShowRequest request) {
        return showService.update(id, request);
    }

    @AdminOnly
    @PatchMapping("/{id}/status")
    public ShowResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ShowStatusRequest request) {
        return showService.changeStatus(id, request.getStatus());
    }
}
