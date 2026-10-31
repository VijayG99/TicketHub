package com.tickethub.event.dto;

import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventResponse {
    private final Long id;
    private final String title;
    private final String description;
    private final EventType type;
    private final EventStatus status;
    private final String posterUrl;
    private final Instant createdAt;
    private final Instant updatedAt;
}
