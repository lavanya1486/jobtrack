package com.jobtrack.dto.auth;

import com.jobtrack.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Public view of an account. Never contains the password hash. */
@Schema(description = "Authenticated user profile")
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        String timezone,
        boolean emailRemindersEnabled,
        Instant createdAt
) {
}
