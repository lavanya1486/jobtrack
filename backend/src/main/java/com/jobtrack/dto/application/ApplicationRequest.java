package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create / update payload for a job application.
 *
 * <p>Used for both {@code POST} and {@code PUT}; cross-field rules (salary range, deadline after
 * application date) are validated here with {@code @AssertTrue} so the failure comes back as a
 * {@code 400} with field-level messages instead of a database constraint violation.</p>
 */
@Schema(description = "Job application create/update request")
public record ApplicationRequest(

        @Schema(example = "Zoho", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Company is required")
        @Size(max = 140, message = "Company must be at most 140 characters")
        String company,

        @Schema(example = "Java Developer Intern", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Job title is required")
        @Size(max = 160, message = "Job title must be at most 160 characters")
        String jobTitle,

        @Schema(description = "Free text copied from the posting")
        @Size(max = 8000, message = "Job description must be at most 8000 characters")
        String jobDescription,

        @Schema(example = "https://careers.zoho.com/job/123")
        @Size(max = 500, message = "Job URL must be at most 500 characters")
        @URL(message = "Job URL must be a valid http(s) URL")
        String jobUrl,

        @Schema(example = "LinkedIn")
        @Size(max = 60)
        String source,

        @Schema(example = "FULL_TIME")
        EmploymentType employmentType,

        @Schema(example = "HYBRID")
        WorkMode workMode,

        @Schema(example = "Chennai, India")
        @Size(max = 160)
        String location,

        @Schema(example = "INR")
        @Size(min = 3, max = 8, message = "Currency must be a 3-8 character code")
        String currency,

        @Schema(example = "400000")
        @DecimalMin(value = "0.0", message = "Minimum expected salary cannot be negative")
        BigDecimal expectedSalaryMin,

        @Schema(example = "600000")
        @DecimalMin(value = "0.0", message = "Maximum expected salary cannot be negative")
        BigDecimal expectedSalaryMax,

        @Schema(example = "APPLIED", description = "SAVED, APPLIED, ASSESSMENT, INTERVIEW, OFFER, REJECTED, WITHDRAWN")
        ApplicationStatus status,

        @Schema(example = "2026-03-02", description = "Date the application was submitted")
        LocalDate appliedAt,

        @Schema(example = "2026-03-20", description = "Posting / process deadline")
        LocalDate deadline,

        @Schema(example = "2026-03-10")
        LocalDate nextActionDate,

        @Schema(example = "Send follow-up e-mail to the recruiter")
        @Size(max = 200)
        String nextAction,

        @Size(max = 5000, message = "Notes must be at most 5000 characters")
        String notes,

        @Schema(example = "[\"referral\", \"spring-boot\"]")
        @Size(max = 15, message = "At most 15 tags are allowed")
        List<@NotBlank(message = "Tag cannot be blank") @Size(max = 40, message = "Tag must be at most 40 characters") String> tags
) {

        @Schema(hidden = true)
        @AssertTrue(message = "Expected salary maximum must be greater than or equal to the minimum")
        boolean isSalaryRangeValid() {
            if (expectedSalaryMin == null || expectedSalaryMax == null) {
                return true;
            }
            return expectedSalaryMax.compareTo(expectedSalaryMin) >= 0;
        }

        @Schema(hidden = true)
        @AssertTrue(message = "Deadline cannot be before the application date")
        boolean isDeadlineAfterApplication() {
            if (deadline == null || appliedAt == null) {
                return true;
            }
            return !deadline.isBefore(appliedAt);
        }

        @Schema(hidden = true)
        @AssertTrue(message = "Next action date cannot be before the application date")
        boolean isNextActionDateSane() {
            if (nextActionDate == null || appliedAt == null) {
                return true;
            }
            return !nextActionDate.isBefore(appliedAt);
        }
}
