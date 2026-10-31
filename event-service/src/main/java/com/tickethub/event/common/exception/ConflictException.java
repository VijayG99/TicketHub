package com.tickethub.event.common.exception;

import com.tickethub.event.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
