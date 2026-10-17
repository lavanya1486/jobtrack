package com.jobtrack.security;

import com.jobtrack.dto.common.ApiError;
import com.jobtrack.web.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answers {@code 401 Unauthorized} with the standard error body.
 *
 * <p>When the JWT filter recorded why the token failed, the reason is echoed back as
 * {@code details.reason = token_expired | token_invalid | user_not_found | account_disabled}. The
 * React client uses that hint to decide
 * between "silently refresh the session" and "send the user to the login screen".</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String reason = (String) request.getAttribute(JwtAuthenticationFilter.ATTR_AUTH_ERROR);
        String message = switch (reason == null ? "" : reason) {
            case JwtAuthenticationFilter.REASON_EXPIRED -> "Access token has expired";
            case JwtAuthenticationFilter.REASON_INVALID -> "Access token is invalid";
            case JwtAuthenticationFilter.REASON_USER_GONE -> "Account no longer exists";
            case JwtAuthenticationFilter.REASON_DISABLED -> "Account is disabled";
            default -> "Authentication required";
        };

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason == null ? "missing_credentials" : reason);
        errorWriter.write(request, response, ApiError.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "unauthorized",
                message,
                request.getRequestURI(),
                details));
    }
}
