package com.jobtrack.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource does not exist <em>for the calling user</em>.
 *
 * <p>Cross-user access is reported as 404 rather than 403 on purpose: returning 403 would confirm
 * that the id exists and belongs to somebody else, which is exactly the signal an IDOR scan looks
 * for. The API documentation states this contract.</p>
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static NotFoundException application(Long id) {
        return new NotFoundException("Application " + id + " not found");
    }

    public static NotFoundException interview(Long id) {
        return new NotFoundException("Interview " + id + " not found");
    }

    public static NotFoundException reminder(Long id) {
        return new NotFoundException("Reminder " + id + " not found");
    }

    public static NotFoundException user(Long id) {
        return new NotFoundException("User " + id + " not found");
    }
}
