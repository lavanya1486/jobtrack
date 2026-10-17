package com.jobtrack.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.dto.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes an {@link ApiError} body from inside the filter chain, where
 * {@code @RestControllerAdvice} cannot reach (Spring Security rejects the request before it hits a
 * controller). Keeps the 401/403 shape identical to every other error response.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
            throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Never let a browser cache an auth failure for an authenticated URL.
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
