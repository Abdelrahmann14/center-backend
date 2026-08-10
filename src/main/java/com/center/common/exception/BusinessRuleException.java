package com.center.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A domain rule rejected the input (bad phone, score above the lesson maximum,
 * price above the center's). 422 mirrors what the previous API returned.
 */
public class BusinessRuleException extends ApplicationException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
