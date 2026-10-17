package com.jobtrack.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up payload.
 *
 * <p>The password rule is intentionally modest (8+ chars, at least one letter and one digit) and is
 * enforced server-side; the plain password is hashed with BCrypt before it ever touches the DB.</p>
 */
@Schema(description = "Registration request")
public record RegisterRequest(

        @Schema(example = "Asha Verma")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters")
        String name,

        @Schema(example = "asha@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 160, message = "Email must be at most 160 characters")
        String email,

        @Schema(example = "Str0ngPass!", description = "8-72 chars, at least one letter and one digit")
        @NotBlank(message = "Password is required")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "Password must be 8-72 characters and contain at least one letter and one digit")
        String password
) {
}
