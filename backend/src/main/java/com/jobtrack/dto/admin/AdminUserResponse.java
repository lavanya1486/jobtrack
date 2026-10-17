package com.jobtrack.dto.admin;

import com.jobtrack.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Row of the admin user table. Includes activity counters but never credentials. */
@Schema(description = "Admin view of a user account")
public record AdminUserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean enabled,
        String timezone,
        long applications,
        long interviews,
        long openReminders,
        Instant createdAt
) {
}
