package com.jobtrack.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Platform-wide counters for the admin page. */
@Schema(description = "Platform statistics (ADMIN only)")
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long adminUsers,
        long totalApplications,
        long submittedApplications,
        long totalInterviews,
        long openReminders,
        long activeRefreshTokens,
        double averageApplicationsPerUser
) {
}
