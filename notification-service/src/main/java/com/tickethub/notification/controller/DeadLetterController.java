package com.tickethub.notification.controller;

import com.tickethub.notification.common.security.AdminOnly;
import com.tickethub.notification.common.web.PageResponse;
import com.tickethub.notification.dto.DeadLetterResponse;
import com.tickethub.notification.dto.ReplayRequest;
import com.tickethub.notification.service.DeadLetterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S1: operator view of messages that failed for good.
 *   GET  /notifications/dead-letters?status=PARKED
 *   POST /notifications/dead-letters/{id}/replay        (body optional: {"payload":"<fixed json>"})
 */
@Tag(name = "Dead letters")
@AdminOnly
@RestController
@RequestMapping("/notifications/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService service;

    @GetMapping
    public PageResponse<DeadLetterResponse> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, pageable);
    }

    @PostMapping("/{id}/replay")
    public DeadLetterResponse replay(@PathVariable UUID id, @RequestBody(required = false) ReplayRequest body) {
        return service.replay(id, body == null ? null : body.getPayload());
    }
}
