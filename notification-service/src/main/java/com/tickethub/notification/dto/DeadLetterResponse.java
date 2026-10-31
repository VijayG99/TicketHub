package com.tickethub.notification.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeadLetterResponse {
    private final UUID id;
    private final String topic;
    private final String key;
    private final String payload;
    private final String error;
    private final String status;
    private final Instant createdAt;
    private final Instant replayedAt;
}
