package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Dashboard KPI payload.
 *
 * <p>Every rate uses the same denominator: {@code submittedApplications} (all applications except
 * {@code SAVED} drafts). Numerators never overlap in a way that could double count one application,
 * because each is a distinct predicate over the same row set. Rates are {@code 0} when the
 * denominator is {@code 0} rather than {@code NaN}.</p>
 */
@Schema(description = "Aggregated job-search KPIs for the authenticated user")
public record AnalyticsSummaryResponse(
        @Schema(description = "All applications including drafts")
        long totalApplications,
        @Schema(description = "Applications actually submitted (status != SAVED) - the rate denominator")
        long submittedApplications,
        long savedDrafts,
        long activeApplications,
        long closedApplications,
        @Schema(description = "Applications with a recorded employer response")
        long respondedApplications,
        @Schema(description = "Applications that reached an interview stage or have interview rounds")
        long reachedInterview,
        long offersReceived,
        long rejections,
        long withdrawals,
        @Schema(description = "Interview rounds booked across all applications")
        long interviewsScheduled,
        long upcomingInterviews,
        long completedInterviews,
        long passedInterviews,
        long failedInterviews,
        @Schema(description = "Open applications whose deadline already passed")
        long overdueApplications,
        long dueNextActions,
        long openReminders,
        long overdueReminders,
        @Schema(description = "Average days between submitting and the first employer response")
        Double averageDaysToFirstResponse,
        @Schema(description = "responseRate = respondedApplications / submittedApplications (0-1)")
        double responseRate,
        @Schema(description = "interviewRate = reachedInterview / submittedApplications (0-1)")
        double interviewRate,
        @Schema(description = "offerRate = offersReceived / submittedApplications (0-1)")
        double offerRate,
        @Schema(description = "Average expected salary maximum across applications that declare one")
        Double averageExpectedSalaryMax,
        List<StatusSliceResponse> statusDistribution,
        List<FunnelStageResponse> funnel,
        @Schema(description = "Companies with the most applications")
        List<TopCompanyResponse> topCompanies
) {
}
