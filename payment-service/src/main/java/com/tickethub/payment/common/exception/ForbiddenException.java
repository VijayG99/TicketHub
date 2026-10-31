package com.tickethub.payment.common.exception;

import com.tickethub.payment.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
