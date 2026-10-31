package com.tickethub.notification.controller;

import com.tickethub.notification.common.security.AdminOnly;
import com.tickethub.notification.common.web.PageResponse;
import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations view only. Notification is event-driven and is not part of the booking critical path. */
@Tag(name = "Notifications")
@AdminOnly
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService queryService;

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) NotificationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.list(bookingId, status, pageable);
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable UUID id) {
        return queryService.get(id);
    }
}
