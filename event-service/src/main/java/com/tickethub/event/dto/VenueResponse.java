package com.tickethub.event.dto;

import com.tickethub.event.enums.VenueStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VenueResponse {
    private final Long id;
    private final String name;
    private final String city;
    private final String address;
    private final VenueStatus status;
    private final Instant createdAt;
}
