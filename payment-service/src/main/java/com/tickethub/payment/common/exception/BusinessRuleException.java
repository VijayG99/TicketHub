package com.tickethub.payment.common.exception;

import com.tickethub.payment.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BusinessException {
    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
