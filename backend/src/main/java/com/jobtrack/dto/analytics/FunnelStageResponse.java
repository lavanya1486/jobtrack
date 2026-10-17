package com.jobtrack.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One stage of the conversion funnel.
 *
 * @param stage             stable machine name, e.g. {@code APPLIED}
 * @param label             human label for the chart
 * @param count             applications that reached this stage
 * @param conversionFromPrevious share of the previous stage that made it here (0-1)
 * @param conversionFromStart    share of all submitted applications (0-1)
 */
@Schema(description = "Funnel stage")
public record FunnelStageResponse(
        String stage,
        String label,
        long count,
        double conversionFromPrevious,
        double conversionFromStart
) {
}
