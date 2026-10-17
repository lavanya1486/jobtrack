package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

/** The request conflicts with existing state, e.g. a duplicate e-mail or a reused refresh token. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }

    public ConflictException(String message, java.util.Map<String, Object> details) {
        super(HttpStatus.CONFLICT, "conflict", message, details);
    }
}
