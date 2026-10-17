package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Malformed or business-rule-violating input. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public BadRequestException(String message, Map<String, Object> details) {
        super(HttpStatus.BAD_REQUEST, "bad_request", message, details);
    }
}
