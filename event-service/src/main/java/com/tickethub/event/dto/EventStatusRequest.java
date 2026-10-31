package com.tickethub.event.dto;

import com.tickethub.event.enums.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventStatusRequest {
    @NotNull(message = "status is required")
    private EventStatus status;
}
