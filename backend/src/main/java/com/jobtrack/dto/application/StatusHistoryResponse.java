package com.jobtrack.dto.application;

import com.jobtrack.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One entry of the application status timeline. */
@Schema(description = "Status change audit entry")
public record StatusHistoryResponse(
        Long id,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String note,
        Instant changedAt
) {
}
