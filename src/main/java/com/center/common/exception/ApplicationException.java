package com.center.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Base for errors that map to a deliberate HTTP status. The message is Arabic
 * because it is rendered to the user verbatim.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    protected ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
