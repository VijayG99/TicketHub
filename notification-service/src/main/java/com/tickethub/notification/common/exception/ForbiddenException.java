package com.tickethub.notification.common.exception;

import com.tickethub.notification.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
