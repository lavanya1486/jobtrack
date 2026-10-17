package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Password change; revokes all refresh tokens on success so other sessions are signed out. */
@Schema(description = "Change password request")
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "New password must be 8-72 characters and contain at least one letter and one digit")
        String newPassword
) {
}
