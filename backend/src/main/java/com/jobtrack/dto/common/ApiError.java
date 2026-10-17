package com.jobtrack.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Single error shape returned by every failing endpoint (see {@code GlobalExceptionHandler}).
 *
 * <p>Clients can rely on {@code status}, {@code error} and {@code message}; {@code fieldErrors} is
 * present only for bean-validation failures and {@code details} carries machine-readable extras
 * such as rejected enum values.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors,
        Map<String, Object> details
) {

    public record FieldError(String field, String message, Object rejectedValue) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null, null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors, null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              Map<String, Object> details) {
        return new ApiError(Instant.now(), status, error, message, path, null, details);
    }
}
