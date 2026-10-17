package com.jobtrack.dto.interview;

import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.RoundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.ZonedDateTime;

/** An interview round, with the owning application denormalised for list views. */
@Schema(description = "Interview round")
public record InterviewResponse(
        Long id,
        Long applicationId,
        String company,
        String jobTitle,
        RoundType roundType,
        Instant scheduledAt,
        @Schema(description = "scheduledAt rendered in the requesting user's time zone")
        ZonedDateTime scheduledAtLocal,
        String timezone,
        int durationMinutes,
        String locationOrLink,
        String interviewerName,
        String prepNotes,
        String feedback,
        InterviewOutcome outcome,
        boolean upcoming,
        Long minutesUntilStart,
        Instant createdAt,
        Instant updatedAt
) {
}
