package com.center.common.exception;

import org.springframework.http.HttpStatus;

/** A uniqueness rule was violated - duplicate name, phone or time slot. */
public class DuplicateResourceException extends ApplicationException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
