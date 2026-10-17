package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One calendar month of activity, used by the monthly trend chart.
 *
 * <p>Months with no activity are emitted with zero counts (the service fills the gaps) so the chart
 * always shows a continuous axis instead of jumping between sparse months.</p>
 */
@Schema(description = "Monthly activity bucket")
public record TrendPointResponse(
        @Schema(example = "2026-03", description = "ISO year-month")
        String month,
        int year,
        int monthValue,
        @Schema(example = "March 2026")
        String label,
        long applications,
        long interviews,
        long responses,
        long offers,
        long rejections
) {
}
