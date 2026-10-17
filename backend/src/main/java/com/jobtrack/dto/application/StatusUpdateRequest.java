package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Explicit status transition ({@code PATCH /api/applications/{id}/status}).
 *
 * <p>Transitions are validated by {@code ApplicationStatusPolicy}: nonsense jumps such as
 * {@code SAVED -> OFFER} are rejected with {@code 400} and a list of the allowed targets.</p>
 */
@Schema(description = "Status transition request")
public record StatusUpdateRequest(

        @Schema(example = "INTERVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Status is required")
        ApplicationStatus status,

        @Schema(example = "Recruiter moved me to the technical round")
        @Size(max = 400, message = "Note must be at most 400 characters")
        String note
) {
}
