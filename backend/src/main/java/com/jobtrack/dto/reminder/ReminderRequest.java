package com.jobtrack.dto.reminder;

import com.jobtrack.domain.ReminderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Create a manual reminder, optionally attached to an application. */
@Schema(description = "Reminder create/update request")
public record ReminderRequest(

        @Schema(example = "FOLLOW_UP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Reminder type is required")
        ReminderType type,

        @Schema(example = "Follow up with the recruiter")
        @NotNull(message = "Title is required")
        @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
        String title,

        @Size(max = 500)
        String details,

        @Schema(example = "2026-03-15T09:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Due date/time is required")
        Instant dueAt,

        @Schema(description = "Application this reminder belongs to; must be owned by the caller")
        Long applicationId
) {
}
