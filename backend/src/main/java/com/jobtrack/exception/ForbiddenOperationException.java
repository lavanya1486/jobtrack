package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Authenticated, but not allowed to perform this action. */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public ForbiddenOperationException(String message, Map<String, Object> details) {
        super(HttpStatus.FORBIDDEN, "forbidden", message, details);
    }
}
