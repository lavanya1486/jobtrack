package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login request")
public record LoginRequest(

        @Schema(example = "asha@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @Schema(example = "Str0ngPass!")
        @NotBlank(message = "Password is required")
        String password
) {
}
