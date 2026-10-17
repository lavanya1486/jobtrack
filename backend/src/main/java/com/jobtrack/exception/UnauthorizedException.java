package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

/** Missing or unusable credentials. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    public UnauthorizedException(String message, java.util.Map<String, Object> details) {
        super(HttpStatus.UNAUTHORIZED, "unauthorized", message, details);
    }
}
