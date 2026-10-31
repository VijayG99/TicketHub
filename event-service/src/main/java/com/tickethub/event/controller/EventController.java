package com.tickethub.event.controller;

import com.tickethub.event.common.security.AdminOnly;
import com.tickethub.event.common.security.CurrentUser;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.EventRequest;
import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.dto.EventStatusRequest;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import com.tickethub.event.service.EventService;
import com.tickethub.event.service.PosterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Events")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final PosterService posterService;

    @GetMapping
    public PageResponse<EventResponse> list(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) EventType type,
            CurrentUser user,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return eventService.list(status, type, user, pageable);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id, CurrentUser user) {
        return eventService.get(id, user);
    }

    @AdminOnly
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return eventService.create(request);
    }

    @AdminOnly
    @PutMapping("/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return eventService.update(id, request);
    }

    @AdminOnly
    @PatchMapping("/{id}/status")
    public EventResponse changeStatus(@PathVariable Long id, @Valid @RequestBody EventStatusRequest request) {
        return eventService.changeStatus(id, request.getStatus());
    }

    /** Uploads the event poster (JPEG/PNG/WebP, max 5 MB) to object storage (ST-30). */
    @AdminOnly
    @PostMapping(value = "/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse uploadPoster(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return posterService.upload(id, file);
    }
}
