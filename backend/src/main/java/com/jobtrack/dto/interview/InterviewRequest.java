package com.jobtrack.dto.interview;

import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.RoundType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

/** Schedule (or reschedule) an interview round for an application. */
@Schema(description = "Interview round create/update request")
public record InterviewRequest(

        @Schema(example = "TECHNICAL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Round type is required")
        RoundType roundType,

        @Schema(example = "2026-03-12T10:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Start time as an ISO-8601 instant (UTC)")
        @NotNull(message = "Scheduled time is required")
        Instant scheduledAt,

        @Schema(example = "45")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        @Max(value = 720, message = "Duration must be at most 720 minutes")
        Integer durationMinutes,

        @Schema(example = "https://meet.google.com/abc-defg-hij")
        @Size(max = 500, message = "Location/link must be at most 500 characters")
        @URL(message = "Meeting link must be a valid http(s) URL")
        String locationOrLink,

        @Schema(example = "Priya Nair")
        @Size(max = 140)
        String interviewerName,

        @Size(max = 4000, message = "Preparation notes must be at most 4000 characters")
        String prepNotes,

        @Size(max = 4000, message = "Feedback must be at most 4000 characters")
        String feedback,

        @Schema(example = "PENDING")
        InterviewOutcome outcome
) {
}
