package com.jobtrack.dto.application;

import com.jobtrack.dto.interview.InterviewResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Everything the application detail page needs in one round trip:
 * the application, its interview rounds and its status timeline.
 */
@Schema(description = "Application with interviews and status history")
public record ApplicationDetailResponse(
        ApplicationResponse application,
        List<InterviewResponse> interviews,
        List<StatusHistoryResponse> history
) {
}
