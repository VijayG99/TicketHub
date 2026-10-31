package com.tickethub.booking.common.exception;

import com.tickethub.booking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class DependencyTimeoutException extends BusinessException {
    public DependencyTimeoutException(String message) {
        super(ErrorCode.DEPENDENCY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, message);
    }
}
