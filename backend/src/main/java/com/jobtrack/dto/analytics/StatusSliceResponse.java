package com.jobtrack.dto.analytics;

import com.jobtrack.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Share of applications in one status - powers the status distribution chart. */
@Schema(description = "Status distribution slice")
public record StatusSliceResponse(
        ApplicationStatus status,
        long count,
        @Schema(description = "Share of all applications, 0-1")
        double share
) {
}
