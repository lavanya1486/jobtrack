package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base type for every error this API raises on purpose.
 *
 * <p>Carrying the {@link HttpStatus} and an optional machine-readable {@code code} means controllers
 * never deal with status codes and {@link GlobalExceptionHandler} is the only place that shapes an
 * error response.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> details;

    protected ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
