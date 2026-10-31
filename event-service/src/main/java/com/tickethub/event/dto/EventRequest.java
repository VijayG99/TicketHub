package com.tickethub.event.dto;

import com.tickethub.event.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventRequest {
    @NotBlank(message = "title is required")
    @Size(max = 200, message = "title must be at most 200 characters")
    private String title;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    @NotNull(message = "type is required")
    private EventType type;

    @Size(max = 500, message = "posterUrl must be at most 500 characters")
    private String posterUrl;
}
