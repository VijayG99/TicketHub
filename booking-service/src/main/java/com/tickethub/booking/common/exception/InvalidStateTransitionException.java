package com.tickethub.booking.common.exception;

import com.tickethub.booking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BusinessException {
    public InvalidStateTransitionException(String message) {
        super(ErrorCode.INVALID_STATE_TRANSITION, HttpStatus.CONFLICT, message);
    }
}
