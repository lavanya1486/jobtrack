package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Application as returned by list / create / update endpoints. */
@Schema(description = "Job application")
public record ApplicationResponse(
        Long id,
        String company,
        String jobTitle,
        String jobDescription,
        String jobUrl,
        String source,
        EmploymentType employmentType,
        WorkMode workMode,
        String location,
        String currency,
        BigDecimal expectedSalaryMin,
        BigDecimal expectedSalaryMax,
        ApplicationStatus status,
        LocalDate appliedAt,
        LocalDate deadline,
        LocalDate nextActionDate,
        String nextAction,
        String notes,
        List<String> tags,
        Instant firstResponseAt,
        Instant createdAt,
        Instant updatedAt,

        // --- derived, computed server-side so the UI never re-implements the rules ---
        @Schema(description = "Deadline passed while the process is still open")
        boolean deadlineOverdue,
        @Schema(description = "Next action date is today or in the past")
        boolean nextActionDue,
        @Schema(description = "Days until the deadline; negative when overdue, null when unset")
        Long daysUntilDeadline,
        @Schema(description = "Days since the application was submitted")
        Long daysSinceApplied,
        @Schema(description = "True when the application counts towards rate denominators")
        boolean submitted,
        @Schema(description = "Number of interview rounds recorded for this application")
        int interviewCount,
        @Schema(description = "Number of open reminders for this application")
        int openReminderCount
) {
}
