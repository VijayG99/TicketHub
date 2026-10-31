package com.tickethub.event.dto;

import com.tickethub.event.enums.VenueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VenueRequest {
    @NotBlank(message = "name is required")
    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    @NotBlank(message = "city is required")
    @Size(max = 100, message = "city must be at most 100 characters")
    private String city;

    @Size(max = 300, message = "address must be at most 300 characters")
    private String address;

    /** Optional on create (defaults to ACTIVE). */
    private VenueStatus status;
}
