package com.tickethub.event.dto;

import com.tickethub.event.enums.ShowStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShowStatusRequest {
    @NotNull(message = "status is required")
    private ShowStatus status;
}
