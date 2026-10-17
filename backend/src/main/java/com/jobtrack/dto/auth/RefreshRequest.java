package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/refresh}. The presented token is rotated (single use). */
@Schema(description = "Refresh token exchange request")
public record RefreshRequest(

        @Schema(description = "Refresh token issued at login or by a previous refresh")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
