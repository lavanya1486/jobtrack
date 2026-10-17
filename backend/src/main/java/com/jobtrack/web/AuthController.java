package com.jobtrack.web;

import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.dto.auth.LoginRequest;
import com.jobtrack.dto.auth.RefreshRequest;
import com.jobtrack.dto.auth.RegisterRequest;
import com.jobtrack.security.RequestMetadata;
import com.jobtrack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>These four are the only unauthenticated routes besides the OpenAPI docs and health probes.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, log in, rotate and revoke tokens")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
            description = "Registers a new USER and returns an access + refresh token pair. "
                    + "Returns 409 when the e-mail is already registered.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        AuthResponse response = authService.register(request, RequestMetadataFactory.of(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Exchanges e-mail + password for a token pair. Returns 401 with the same "
                    + "message for unknown e-mail, disabled account and wrong password.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, RequestMetadataFactory.of(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token",
            description = "Consumes the presented refresh token and returns a new pair. Presenting an "
                    + "already-used token revokes the whole session family (reuse detection).")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request, RequestMetadataFactory.of(http));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token",
            description = "Idempotent: an unknown or already revoked token still answers 204.")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Captures non-sensitive request metadata (user agent, client IP) for session auditing. */
    static final class RequestMetadataFactory {

        private RequestMetadataFactory() {
        }

        static RequestMetadata of(HttpServletRequest request) {
            return new RequestMetadata(request.getHeader("User-Agent"), clientIp(request));
        }

        private static String clientIp(HttpServletRequest request) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }
}
