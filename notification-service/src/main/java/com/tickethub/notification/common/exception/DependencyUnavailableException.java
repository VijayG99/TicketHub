package com.tickethub.notification.common.exception;

import com.tickethub.notification.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class DependencyUnavailableException extends BusinessException {
    public DependencyUnavailableException(String message) {
        super(ErrorCode.DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
