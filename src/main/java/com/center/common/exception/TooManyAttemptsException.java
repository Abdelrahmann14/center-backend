package com.center.common.exception;

import org.springframework.http.HttpStatus;

/** Too many tries in too little time - the caller must wait before retrying. */
public class TooManyAttemptsException extends ApplicationException {

    public TooManyAttemptsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
