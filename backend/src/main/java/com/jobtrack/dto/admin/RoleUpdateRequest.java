package com.jobtrack.dto.admin;

import com.jobtrack.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** ADMIN-only role change. */
@Schema(description = "Change a user's role")
public record RoleUpdateRequest(

        @Schema(example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Role is required")
        Role role
) {
}
