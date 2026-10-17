package com.jobtrack.dto.reminder;

import com.jobtrack.domain.ReminderSource;
import com.jobtrack.domain.ReminderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.ZonedDateTime;

/** A reminder, plus everything the dashboard needs to group and sort it. */
@Schema(description = "Reminder")
public record ReminderResponse(
        Long id,
        Long applicationId,
        String company,
        String jobTitle,
        ReminderType type,
        ReminderSource source,
        String title,
        String details,
        Instant dueAt,
        @Schema(description = "dueAt rendered in the requesting user's time zone")
        ZonedDateTime dueAtLocal,
        String timezone,
        Instant completedAt,
        Instant notifiedAt,
        Instant emailSentAt,
        boolean completed,
        boolean overdue,
        Long hoursUntilDue,
        Instant createdAt
) {
}
