package com.jobtrack.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.jobtrack.dto.common.ApiError;
import com.jobtrack.dto.common.ApiError.FieldError;
import com.jobtrack.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single place that turns exceptions into the documented {@link ApiError} body.
 *
 * <p>Rules followed here:</p>
 * <ul>
 *   <li>Client mistakes return the accurate 4xx and a message safe to show a user.</li>
 *   <li>Validation failures list every offending field, so a form can render inline errors.</li>
 *   <li>Unexpected exceptions log the stack trace but return a generic 500 - internals are never
 *       leaked to the client.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ------------------------------------------------------------ intentional errors ----

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            log.error("API error {} on {} {}", status, request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.debug("API error {} on {} {}: {}", status, request.getMethod(),
                    request.getRequestURI(), ex.getMessage());
        }
        // `error` carries the machine-readable code (bad_request, not_found, conflict, ...);
        // `details` only adds context such as the rejected value or the allowed transitions.
        Map<String, Object> details = ex.getDetails() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(ex.getDetails());
        return ResponseEntity.status(status)
                .body(new ApiError(java.time.Instant.now(), status.value(), ex.getCode(),
                        ex.getMessage(), path(request), null,
                        details.isEmpty() ? null : details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(403, "forbidden",
                "You do not have permission to perform this action", path(request)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(401, "unauthorized",
                "Authentication failed", path(request)));
    }

    // ------------------------------------------------------------ validation errors ----

    /** {@code @Valid @RequestBody} failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), firstMessage(fe.getDefaultMessage()),
                        safeValue(fe.getRejectedValue())))
                .toList();
        ex.getBindingResult().getGlobalErrors().forEach(ge -> fieldErrors.add(
                new FieldError(ge.getObjectName(), firstMessage(ge.getDefaultMessage()), null)));
        return ResponseEntity.badRequest().body(ApiError.of(400, "validation_failed",
                "Request validation failed", path(request), fieldErrors));
    }

    /** {@code @Validated} failures on {@code @RequestParam} / {@code @PathVariable}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerValidation(HandlerMethodValidationException ex,
                                                            HttpServletRequest request) {
        List<FieldError> fieldErrors = new java.util.ArrayList<>();
        ex.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                fieldErrors.add(new FieldError(result.getMethodParameter().getParameterName(),
                        firstMessage(error.getDefaultMessage()), null))));
        return ResponseEntity.badRequest().body(ApiError.of(400, "validation_failed",
                "Request validation failed", path(request), fieldErrors));
    }

    /** Bean-validation failures raised outside a controller binding (e.g. in a service). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of(400, "validation_failed",
                "Request validation failed", path(request), fieldErrors));
    }

    private FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return new FieldError(field, firstMessage(violation.getMessage()),
                safeValue(violation.getInvalidValue()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        List<FieldError> fieldErrors = List.of(
                new FieldError(ex.getParameterName(), "Parameter is required", null));
        return ResponseEntity.badRequest().body(ApiError.of(400, "validation_failed",
                "Required parameter is missing", path(request), fieldErrors));
    }

    // ------------------------------------------------------------ parsing errors ----

    /** Malformed JSON body or a value that does not match the target type (enum, date, number). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        if (ex.getCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null && invalid.getTargetType().isEnum()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("acceptedValues", enumValues(invalid.getTargetType()));
            details.put("field", invalid.getPath().isEmpty() ? null
                    : invalid.getPath().getFirst().getFieldName());
            return ResponseEntity.badRequest().body(ApiError.of(400, "invalid_enum_value",
                    "'" + invalid.getValue() + "' is not a valid "
                            + invalid.getTargetType().getSimpleName(), path(request), details));
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "malformed_request",
                "Request body could not be parsed", path(request)));
    }

    /** Bad query parameter or path variable (unknown status, unparsable date, non-numeric id). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        Class<?> required = ex.getRequiredType();
        String message;
        if (required != null && required.isEnum()) {
            details.put("acceptedValues", enumValues(required));
            message = "'" + ex.getValue() + "' is not a valid " + required.getSimpleName();
        } else if (ex.getCause() instanceof IllegalArgumentException iae) {
            message = iae.getMessage();
        } else {
            message = "Parameter '" + ex.getName() + "' has an invalid value";
        }
        details.put("parameter", ex.getName());
        return ResponseEntity.badRequest().body(ApiError.of(400, "invalid_parameter",
                message, path(request), details));
    }

    // ------------------------------------------------------------ protocol errors ----

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, "not_found",
                "Resource not found", path(request)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("supportedMethods", ex.getSupportedMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(405,
                "method_not_allowed", ex.getMessage(), path(request), details));
    }

    /** Reached only when a database constraint fires despite service-level checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_users_email")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "conflict",
                    "An account with this e-mail already exists", path(request)));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "conflict",
                "The request conflicts with existing data", path(request)));
    }

    // ------------------------------------------------------------ everything else ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(500,
                "internal_error", "Something went wrong on our side", path(request)));
    }

    // ------------------------------------------------------------ helpers ----

    private static String path(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private static List<String> enumValues(Class<?> type) {
        return Arrays.stream(type.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList();
    }

    private static String firstMessage(String message) {
        if (message == null) {
            return "Invalid value";
        }
        int comma = message.indexOf(',');
        return comma > 0 ? message.substring(0, comma).trim() : message.trim();
    }

    private static Object safeValue(Object value) {
        if (value instanceof String s && s.length() > 200) {
            return s.substring(0, 200) + "...";
        }
        if (value instanceof char[] || value instanceof byte[]) {
            return "[redacted]";
        }
        return value;
    }
}
