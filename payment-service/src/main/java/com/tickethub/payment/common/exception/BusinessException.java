package com.tickethub.payment.common.exception;

import com.tickethub.payment.common.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base type for all expected, controlled failures. Carries the HTTP status and error code. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public BusinessException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
