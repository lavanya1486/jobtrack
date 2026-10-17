package com.jobtrack.web;

import com.jobtrack.dto.analytics.AnalyticsSummaryResponse;
import com.jobtrack.dto.analytics.AnalyticsTrendsResponse;
import com.jobtrack.dto.analytics.UpcomingEventResponse;
import com.jobtrack.security.AuthenticatedUser;
import com.jobtrack.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard analytics.
 *
 * <p>Every value is aggregated in PostgreSQL - no hardcoded numbers, no client-side maths. The metric
 * definitions are documented in the OpenAPI description and implemented once in
 * {@link AnalyticsService}.</p>
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Analytics", description = "KPIs, trends and the conversion funnel")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Dashboard KPI data",
            description = """
                    Counters, rates, status distribution, conversion funnel and top companies.

                    Rates share one denominator - `submittedApplications` (everything except `SAVED`
                    drafts):
                    * `responseRate` = respondedApplications / submittedApplications
                    * `interviewRate` = reachedInterview / submittedApplications
                    * `offerRate` = offersReceived / submittedApplications

                    Rates are 0 (not null, not NaN) when nothing has been submitted yet.
                    """)
    public AnalyticsSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return analyticsService.summary(currentUser);
    }

    @GetMapping("/trends")
    @Operation(summary = "Monthly trends",
            description = "Applications, interviews, responses, offers and rejections per calendar "
                    + "month. Months without activity are returned as zeros so the chart axis is continuous.")
    public AnalyticsTrendsResponse trends(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "6") @Min(1) @Max(36) int months) {
        return analyticsService.trends(currentUser, months);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Upcoming and overdue items",
            description = "Merged, urgency-sorted feed of interviews, deadlines, next actions and "
                    + "overdue reminders for the dashboard sidebar.")
    public List<UpcomingEventResponse> upcoming(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "8") @Min(1) @Max(25) int limit) {
        return analyticsService.upcomingEvents(currentUser, days, limit);
    }
}
