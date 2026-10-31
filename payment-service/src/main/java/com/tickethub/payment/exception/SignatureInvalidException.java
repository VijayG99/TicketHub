package com.tickethub.payment.exception;

import com.tickethub.payment.common.error.ErrorCode;
import com.tickethub.payment.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SignatureInvalidException extends BusinessException {

    public SignatureInvalidException() {
        super(ErrorCode.SIGNATURE_INVALID, HttpStatus.UNAUTHORIZED, "Webhook signature is missing or invalid");
    }
}
