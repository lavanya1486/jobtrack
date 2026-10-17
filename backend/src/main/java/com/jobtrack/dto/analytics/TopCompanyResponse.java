package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

/** Company with the highest number of applications - shown on the dashboard. */
@Schema(description = "Top company by application count")
public record TopCompanyResponse(
        String company,
        long applications,
        long interviews,
        long offers
) {
}
