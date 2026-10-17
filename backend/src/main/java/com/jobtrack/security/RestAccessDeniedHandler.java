package com.jobtrack.security;

import com.jobtrack.dto.common.ApiError;
import com.jobtrack.web.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers {@code 403 Forbidden} when an authenticated caller lacks the required role
 * (e.g. a {@code USER} calling {@code /api/admin/users}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Forbidden {} {} for {}", request.getMethod(), request.getRequestURI(),
                request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName());
        errorWriter.write(request, response, ApiError.of(
                HttpServletResponse.SC_FORBIDDEN,
                "forbidden",
                "You do not have permission to perform this action",
                request.getRequestURI()));
    }
}
