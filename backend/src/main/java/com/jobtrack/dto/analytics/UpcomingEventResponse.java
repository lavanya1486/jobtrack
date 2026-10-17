package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A dashboard "what needs my attention" row: an upcoming interview, an approaching deadline or a
 * due next action. Kept generic so one query can feed one list.
 *
 * @param kind {@code INTERVIEW}, {@code DEADLINE}, {@code NEXT_ACTION} or {@code REMINDER}
 */
@Schema(description = "Upcoming or overdue item for the dashboard")
public record UpcomingEventResponse(
        String kind,
        Long id,
        Long applicationId,
        String company,
        String jobTitle,
        String title,
        @Schema(description = "For date-based kinds")
        LocalDate date,
        @Schema(description = "For time-based kinds")
        Instant at,
        String label,
        boolean overdue,
        Long daysAway
) {
}
