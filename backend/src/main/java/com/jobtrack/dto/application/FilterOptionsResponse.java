package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.EmploymentType;
import com.jobtrack.domain.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Values the filter bar can offer, all derived from the caller's own data.
 *
 * <p>Serving these from the API (instead of hardcoding them in React) means a user only ever sees
 * locations, companies and tags that actually exist in their tracker.</p>
 */
@Schema(description = "Available filter values for the authenticated user")
public record FilterOptionsResponse(
        List<ApplicationStatus> statuses,
        List<EmploymentType> employmentTypes,
        List<WorkMode> workModes,
        List<String> tags,
        List<String> locations,
        List<String> companies
) {
}
