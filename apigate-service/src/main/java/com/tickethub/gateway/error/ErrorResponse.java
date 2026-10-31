package com.tickethub.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final ErrorCode code;
    private final String message;
    private final String path;
    private final String correlationId;
}
