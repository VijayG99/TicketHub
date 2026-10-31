package com.tickethub.notification.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** The single error format used by every TicketHub service (ST-02 / ST-50). */
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
    private final List<FieldViolation> fieldErrors;
}
