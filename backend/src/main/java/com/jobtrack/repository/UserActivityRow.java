package com.jobtrack.repository;

import com.jobtrack.domain.Role;

import java.time.Instant;

/** Row of the admin user table (see {@link UserRepository#findAllWithActivity()}). */
public record UserActivityRow(
        Long id,
        String name,
        String email,
        Role role,
        boolean enabled,
        String timezone,
        Instant createdAt,
        long applications,
        long interviews,
        long openReminders
) {
}
