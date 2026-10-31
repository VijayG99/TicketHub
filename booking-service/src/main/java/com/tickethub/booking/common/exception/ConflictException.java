package com.tickethub.booking.common.exception;

import com.tickethub.booking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
