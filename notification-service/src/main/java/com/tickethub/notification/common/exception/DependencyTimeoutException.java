package com.tickethub.notification.common.exception;

import com.tickethub.notification.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class DependencyTimeoutException extends BusinessException {
    public DependencyTimeoutException(String message) {
        super(ErrorCode.DEPENDENCY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, message);
    }
}
