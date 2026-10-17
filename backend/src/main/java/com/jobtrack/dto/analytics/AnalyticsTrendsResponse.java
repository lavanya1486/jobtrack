package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Payload of {@code GET /api/analytics/trends?months=6}.
 *
 * @param monthly    oldest-first, gap-filled list of monthly buckets
 * @param months     number of months covered (including the current one)
 * @param windowStart instant the window begins
 * @param totals     column totals so the UI can show a summary line without re-summing
 */
@Schema(description = "Monthly application trends")
public record AnalyticsTrendsResponse(
        List<TrendPointResponse> monthly,
        int months,
        Instant windowStart,
        TrendTotals totals
) {

    /** Sums across the requested window. */
    @Schema(description = "Totals for the trend window")
    public record TrendTotals(
            long applications,
            long interviews,
            long responses,
            long offers,
            long rejections,
            @Schema(description = "Best month by applications submitted")
            String bestMonth
    ) {
    }
}
