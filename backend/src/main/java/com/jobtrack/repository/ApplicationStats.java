package com.jobtrack.repository;

/**
 * One-pass aggregate over a user's applications, used by {@code GET /api/analytics/summary}.
 *
 * <p>All counters are computed in the database with {@code sum(case when ...)} so the service never
 * loads entities into memory. Every rate denominator is {@code submitted} (status != SAVED), which
 * keeps the response / interview / offer rates mutually consistent and free of double counting.</p>
 *
 * <p>Built by {@link JobApplicationRepository#aggregateStats} from the raw aggregate row.</p>
 */
public record ApplicationStats(
        long total,
        long submitted,
        long saved,
        long active,
        long closed,
        long responded,
        long offers,
        long rejections,
        long withdrawals,
        long overdueDeadline,
        long dueNextAction,
        Double averageSalaryMax
) {
}
